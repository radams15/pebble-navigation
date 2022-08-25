package com.webmajstr.pebble_gc

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 1)
            }
        }

        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)

        val unitSpinner = findViewById<Spinner>(R.id.unitSpinner)

        ArrayAdapter.createFromResource(this, R.array.Units, android.R.layout.simple_spinner_item).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            unitSpinner.adapter = adapter
        }

        val chosenUnit = prefs.getInt("units", UnitOps.convert(Units.METRIC))
        unitSpinner.setSelection(chosenUnit)

        unitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {

            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Log.i("Item", position.toString())

                val prefEditor = prefs.edit()
                prefEditor.putInt("units", position);
                prefEditor.apply()
            }
        }
    }
}