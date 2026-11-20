package com.archer.ai;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;

public class QiblaActivity extends Activity implements SensorEventListener {
    private SensorManager sensorManager;
    private TextView qiblaText;
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private boolean hasGravity = false, hasGeomagnetic = false;

    // Dhamrai coordinates
    private static final double USER_LAT = 23.92, USER_LON = 90.21;
    private static final double KAABA_LAT = 21.4225, KAABA_LON = 39.8262;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qibla);
        qiblaText = findViewById(R.id.qiblaText);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        double bearing = qiblaBearing();
        qiblaText.setText("কিবলার দিক: উত্তর থেকে " + Math.round(bearing) + "°\nফোন ঘোরাও — এখনকার দিক নিচে দেখাবে।");
    }

    private double qiblaBearing() {
        double lat1 = Math.toRadians(USER_LAT);
        double lat2 = Math.toRadians(KAABA_LAT);
        double dLon = Math.toRadians(KAABA_LON - USER_LON);
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        double brng = Math.toDegrees(Math.atan2(y, x));
        return (brng + 360) % 360;
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values.clone();
            hasGravity = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values.clone();
            hasGeomagnetic = true;
        }
        if (hasGravity && hasGeomagnetic) {
            float[] R = new float[9];
            float[] I = new float[9];
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                double azimuth = (Math.toDegrees(orientation[0]) + 360) % 360;
                double target = qiblaBearing();
                double diff = Math.abs(azimuth - target);
                if (diff > 180) diff = 360 - diff;
                qiblaText.setText("কিবলার দিক: " + Math.round(target) + "°\nফোন এখন: " + Math.round(azimuth) + "°\n"
                        + (diff < 5 ? "✅ ঠিক দিকে আছো!" : "আরও " + Math.round(diff) + "° ঘোরাও"));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
