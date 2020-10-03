#include "pebble.h"
#include <string.h>
#include <math.h>

#define IMPERIAL 0 // foot/mile
#define METRIC 1 // metre/kilometre
#define MIXED 2 // metre/mile

#define METRE_PER_KILOMETRE 1000.0f // when to turn m to km
#define FEET_PER_MILE 5280.0f // when to turn ft to mi

#define FEET_PER_METRE 3.281

#define CHANGE_UNITS 1 // if to change units such as m -> km, ft -> mi

#ifdef PBL_COLOR
  #define bg GColorDukeBlue
  #define fg GColorWhite
#else
  #define fg GColorWhite
  #define bg GColorBlack
#endif

// ADDED GPath point definitions for arrow and north marker
static const GPathInfo ARROW_POINTS =
{
  14,
  (GPoint []) {
    {0, -30},
    {10, -20},
    {6, -16},
    {3, -20},
    {3, 10},
    {7, 15},
    {7, 30},
    {0, 22},
    {-7, 30},
    {-7, 15},
    {-3, 10},
    {-3, -20},
    {-6, -16},
    {-10, -20},
  }
};
static const GPathInfo NORTH_POINTS =
{
  3,
  (GPoint []) {
    {0, -40},
    {6, -31},
    {-6, -31},
  }
};

// updated to match new Android app
enum GeoKey {
  DISTANCE_KEY = 0x0,
  BEARING_INDEX_KEY = 0x1,
  EXTRAS_KEY = 0x2,
  DT_RATING_KEY = 0x3,
  GC_NAME_KEY = 0x4,
  GC_CODE_KEY = 0x5,
  GC_SIZE_KEY = 0x6,
  AZIMUTH_KEY = 0x7,
  DECLINATION_KEY = 0x8,
  UNITS_KEY = 0x9
};

Window *window;

// ADDED GPath definitions
Layer *arrow_layer;
GPath *arrow;
GPath *north;
GRect arrow_bounds;
GPoint arrow_centre;

int16_t compass_heading = 0;
int16_t north_heading = 0;
int16_t bearing = 0;
uint8_t status;
bool rot_arrow = false;
bool gotdecl = false;
int16_t declination = 0;
int16_t units = METRIC;

double distance = 0;

TextLayer *text_distance_layer;
TextLayer *text_time_layer;
Layer *line_layer;

static uint8_t data_display = 0; // default for the bottom info bar

static AppSync sync;
static uint8_t sync_buffer[150];

void handle_minute_tick(struct tm *tick_time, TimeUnits units_changed);

char disp_buf[32];

static void update_display(){    
	if(distance == -1){
		snprintf(disp_buf, sizeof(disp_buf), "%s", "No Signal");
	}else{
		float display_dist;
		const char* unit;
		if(units == IMPERIAL){
			display_dist = distance * FEET_PER_METRE; // turn to feet
			
			if(display_dist >= FEET_PER_MILE && CHANGE_UNITS){
				display_dist /= FEET_PER_MILE; // turn to mile
				unit = "mi";
			}else{
				unit = "ft";
			}
		}else if(units == MIXED){
			display_dist = distance * FEET_PER_METRE; // turn to feet
			
			if(display_dist >= FEET_PER_MILE && CHANGE_UNITS){
				display_dist /= FEET_PER_MILE; // turn to mile
				unit = "mi";
			}else{
				unit = "m";
			}
		}else{ // metric or otherwise
			display_dist = distance;
			if(display_dist >= METRE_PER_KILOMETRE && CHANGE_UNITS){
				display_dist /= METRE_PER_KILOMETRE;
				unit = "km";
			}else{
				unit = "m";
			}
		}
		
		display_dist = roundf(display_dist*100)/100;
		
		snprintf(disp_buf, sizeof(disp_buf), "%d.%d %s", (int)(display_dist), (int)(display_dist*1000)%1000, unit);
		printf("Display: %s\n", disp_buf);
	}
	text_layer_set_text(text_distance_layer, disp_buf);
}

static void sync_tuple_changed_callback(const uint32_t key, const Tuple* new_tuple, const Tuple* old_tuple, void* context){
  switch (key) {

    case DISTANCE_KEY:
	  printf(" ");
      const char* distanceData = new_tuple->value->cstring;

      if (strncmp(distanceData, "GPS", 3) == 0){ // if the distance data starts with GPS
        snprintf(disp_buf, sizeof(disp_buf), "%s", distanceData);
        APP_LOG(APP_LOG_LEVEL_DEBUG, "No Connection Data Recieved");
      }else{
        APP_LOG(APP_LOG_LEVEL_DEBUG, "Actual Distance Data Recieved");
        distance = (new_tuple->value->int32)/1000.0f;
        update_display();
      }
      
      rot_arrow = (strncmp(text_layer_get_text(text_distance_layer), "GPS", 3) != 0) ? true : false;
      break;

    case AZIMUTH_KEY:
      bearing = new_tuple->value->int16;
      layer_mark_dirty(arrow_layer);
      break;

    case DECLINATION_KEY:
      declination = new_tuple->value->int16;
      gotdecl = true;
      layer_mark_dirty(arrow_layer);
      break;
      
	case UNITS_KEY:
		units = new_tuple->value->uint8;
		printf("Units: %d\n", units);
		break;
  }
}

// Draw line between geocaching data and time
void line_layer_update_callback(Layer *layer, GContext* ctx) {
  graphics_context_set_fill_color(ctx, fg);
  graphics_fill_rect(ctx, layer_get_bounds(layer), 0, GCornerNone);
}

// compass handler
void handle_compass(CompassHeadingData heading_data){
  north_heading = TRIGANGLE_TO_DEG(heading_data.magnetic_heading) - declination;
  if (north_heading >= 360) north_heading -= 360;
  if (north_heading < 0) north_heading += 360;
  status = heading_data.compass_status;
  layer_mark_dirty(arrow_layer);
}

// arrow layer update handler
void arrow_layer_update_callback(Layer *path, GContext *ctx) {

  graphics_context_set_fill_color(ctx, fg);
  graphics_context_set_stroke_color(ctx, fg);

  compass_heading = bearing + north_heading;
  if (compass_heading >= 360) {
    compass_heading = compass_heading - 360;
  }

// Don't rotate the arrow if we have NO GPS or NO BT in the distance box
  if (rot_arrow) {
    gpath_rotate_to(arrow, compass_heading * TRIG_MAX_ANGLE / 360);
  }

// draw outline arrow if pebble compass is invalid, solid if OK
  if (status == 0) {
    gpath_draw_outline(ctx, arrow);
  } else {
    gpath_draw_filled(ctx, arrow);
  }

// draw outline north arrow if pebble compass is fine calibrating, solid if good
  gpath_rotate_to(north, north_heading * TRIG_MAX_ANGLE / 360);
  if (!gotdecl) {
    gpath_draw_outline(ctx, north);
  } else {
    gpath_draw_filled(ctx, north);
  }
}

void bluetooth_connection_changed(bool connected) {

  if (!connected) {
    text_layer_set_text(text_distance_layer, "BT Lost");

    //vibes_short_pulse();

  } else {
    const Tuple *tuple = app_sync_get(&sync, DISTANCE_KEY);
    text_layer_set_text(text_distance_layer, (tuple == NULL) ? "GPS Lost" : tuple->value->cstring);
  }
  if (strncmp(text_layer_get_text(text_distance_layer), "NO", 2) != 0) {
    rot_arrow = true;
  } else {
    rot_arrow = false;
  }
}

bool have_additional_data(){
  const Tuple *tuple = app_sync_get(&sync, EXTRAS_KEY);
  return (tuple == NULL || tuple->value->uint8 == 0)? false : true;
}

void handle_display(){
  if(data_display == 0){ // time and date
    tick_timer_service_subscribe(MINUTE_UNIT, handle_minute_tick);

  } else if(data_display == 1){ // geocache name

    const Tuple *tuple = app_sync_get(&sync, GC_NAME_KEY);
    const char *gc_data = tuple == NULL ? "" : tuple->value->cstring;
    text_layer_set_text(text_time_layer, *gc_data ? gc_data : "");

  } else if(data_display == 2){ // geocache code

    const Tuple *tuple = app_sync_get(&sync, GC_CODE_KEY);
    const char *gc_data = tuple == NULL ? "" : tuple->value->cstring;
    text_layer_set_text(text_time_layer, *gc_data ? gc_data : "");

  } else if(data_display == 3){ // geocache size

    const Tuple *tuple = app_sync_get(&sync, GC_SIZE_KEY);
    const char *gc_data = tuple == NULL ? "" : tuple->value->cstring;
    text_layer_set_text(text_time_layer, *gc_data ? gc_data : "");

  } else if(data_display == 4){ // difficulty

    tick_timer_service_unsubscribe();

    const Tuple *tuple = app_sync_get(&sync, DT_RATING_KEY);
    const char *gc_data = tuple == NULL ? "" : tuple->value->cstring;
    text_layer_set_text(text_time_layer, *gc_data ? gc_data : "");

  }
}

void up_click_handler(ClickRecognizerRef recognizer, void *context) {

  data_display++;
  data_display %= 5;

  handle_display();

}

void down_click_handler(ClickRecognizerRef recognizer, void *context) {

  if(data_display == 0) data_display = 5;
  data_display--;

  handle_display();
}

void select_click_handler(ClickRecognizerRef recognizer, void *context) {
  /*APP_LOG(APP_LOG_LEVEL_DEBUG, "Changing Unit Mode From: %d", unitMode);
  unitMode += 1;
  if(unitMode > unitAmount-1){ // if max of the unit array
    unitMode = 0;
  }*/
}



void config_buttons_provider(void *context) {
   window_single_click_subscribe(BUTTON_ID_UP, up_click_handler);
   window_single_click_subscribe(BUTTON_ID_DOWN, down_click_handler);
   window_single_click_subscribe(BUTTON_ID_SELECT, select_click_handler);
   APP_LOG(APP_LOG_LEVEL_DEBUG, "Button Subscription Complete");
 }

void handle_minute_tick(struct tm *tick_time, TimeUnits units_changed) {
  static char time_text[] = "XXX XX 00:00";

  char *time_format;

  if (clock_is_24h_style()) {
    time_format = "%b %e %R";
  } else {
    time_format = "%b %e %I:%M";
  }

  strftime(time_text, sizeof(time_text), time_format, tick_time);

  if (!clock_is_24h_style() && (time_text[0] == '0')) {
    text_layer_set_text(text_time_layer, time_text + 1);
  } else {
    text_layer_set_text(text_time_layer, time_text);
  }
}

void handle_init(void) {
  window = window_create();

  window_set_background_color(window, bg);

  window_stack_push(window, true);

  Layer *window_layer = window_get_root_layer(window);

  // Initialize distance layout
  Layer *distance_holder = layer_create(GRect(0, 80, 144, 40));
  layer_add_child(window_layer, distance_holder);

  ResHandle roboto_36 = resource_get_handle(RESOURCE_ID_FONT_ROBOTO_CONDENSED_36);
  text_distance_layer = text_layer_create(GRect(0, 0, 144, 40));
  text_layer_set_text_color(text_distance_layer, fg);
  text_layer_set_text_alignment(text_distance_layer, GTextAlignmentCenter);
  text_layer_set_background_color(text_distance_layer, GColorClear);
  text_layer_set_font(text_distance_layer, fonts_load_custom_font(roboto_36));
  layer_add_child(distance_holder, text_layer_get_layer(text_distance_layer));

  // Initialize time layout
// Size adjusted so the date and time will fit
  Layer *date_holder = layer_create(GRect(0, 132, 144, 36));
  layer_add_child(window_layer, date_holder);

  line_layer = layer_create(GRect(8, 0, 144-16, 2));
  layer_set_update_proc(line_layer, line_layer_update_callback);
  layer_add_child(date_holder, line_layer);

  ResHandle roboto_22 = resource_get_handle(RESOURCE_ID_FONT_ROBOTO_BOLD_SUBSET_22);
  text_time_layer = text_layer_create(GRect(0, 2, 144, 32));
  text_layer_set_text_color(text_time_layer, fg);
  text_layer_set_text_alignment(text_time_layer, GTextAlignmentCenter);
  text_layer_set_background_color(text_time_layer, GColorClear);
  text_layer_set_font(text_time_layer, fonts_load_custom_font(roboto_22));
  layer_add_child(date_holder, text_layer_get_layer(text_time_layer));

  // Initialize compass layout
  Layer *compass_holder = layer_create(GRect(0, 0, 144, 79));
  layer_add_child(window_layer, compass_holder);

// deleted bitmap layer create

// definitions for Path layer, adjusted size due to a glitch drawing the North dot
  arrow_layer = layer_create(GRect(0, 0, 144, 79));
  layer_set_update_proc(arrow_layer, arrow_layer_update_callback);
  layer_add_child(compass_holder, arrow_layer);

// centrepoint added for use in rotations
  arrow_bounds = layer_get_frame(arrow_layer);
  arrow_centre = GPoint(arrow_bounds.size.w/2, arrow_bounds.size.h/2);

// Initialize and define the paths
  arrow = gpath_create(&ARROW_POINTS);
  gpath_move_to(arrow, arrow_centre);
  north = gpath_create(&NORTH_POINTS);
  gpath_move_to(north, arrow_centre);

  Tuplet initial_values[] = {
    TupletCString(DISTANCE_KEY, "NO GPS"),
    TupletInteger(BEARING_INDEX_KEY, 0),
    TupletInteger(EXTRAS_KEY, 0),
    TupletCString(DT_RATING_KEY, ""),
    TupletCString(GC_NAME_KEY, ""),
    TupletCString(GC_CODE_KEY, ""),
    TupletCString(GC_SIZE_KEY, ""),
    TupletInteger(AZIMUTH_KEY, 0),
    TupletCString(DECLINATION_KEY, "D"),
    TupletInteger(UNITS_KEY, 0),
  };

  const int inbound_size = 150;
  const int outbound_size = 0;
  app_message_open(inbound_size, outbound_size);

  app_sync_init(&sync, sync_buffer, sizeof(sync_buffer), initial_values,
                ARRAY_LENGTH(initial_values), sync_tuple_changed_callback,
                NULL, NULL);

  // Subscribe to notifications
  bluetooth_connection_service_subscribe(bluetooth_connection_changed);
  //tick_timer_service_subscribe(MINUTE_UNIT, handle_minute_tick);
  handle_display();
// compass service added
  compass_service_subscribe(handle_compass);
  compass_service_set_heading_filter(2 * (TRIG_MAX_ANGLE/360));

  window_set_click_config_provider(window, config_buttons_provider);

}

void handle_deinit(void) {
// added unsubscribes
  bluetooth_connection_service_unsubscribe();
  tick_timer_service_unsubscribe();
  compass_service_unsubscribe();

// added layer destroys
  text_layer_destroy(text_time_layer);
  layer_destroy(line_layer);
  text_layer_destroy(text_distance_layer);
  gpath_destroy(arrow);
  layer_destroy(arrow_layer);
  window_destroy(window);

}

int main(void) {
  handle_init();

  app_event_loop();

  handle_deinit();
}
