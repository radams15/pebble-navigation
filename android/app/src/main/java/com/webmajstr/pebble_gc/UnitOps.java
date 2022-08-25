package com.webmajstr.pebble_gc;

public class UnitOps{
    public static int convert(Units units){
        switch(units){
            case IMPERIAL:
                return 0;
            case METRIC:
                return 1;
            case MIXED:
                return 2;
            default:
                return convert(Units.METRIC);
        }
    }

    public static Units convert(int num){
        switch(num){
            case 0:
                return Units.IMPERIAL;
            case 2:
                return Units.MIXED;
            default: // for 1 and other - use metric
                return Units.METRIC;
        }
    }
}