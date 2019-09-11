package com.andreasgift.targetapp;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackingActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "TrackingActivity";
    private SharedPreferences mPrefs;

    private final int MY_PERMISSIONS_REQUEST = 1001;
    private static final int GOOGLE_PLAY_RES_REQUEST = 7172;

    private GoogleMap mMap;
    private DatabaseReference locations;
    private DatabaseReference order;

    private LatLng mCurrentLocation;
    private LatLng mDestination;
    private Tracker currentOrder;

    private TextView destinationTV;
    private Button finishJourneyButton;
    private boolean inJourney = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        destinationTV = findViewById(R.id.textView);
        finishJourneyButton = findViewById(R.id.finish_button);

        locations = FirebaseDatabase.getInstance().getReference("Locations");

        order = FirebaseDatabase.getInstance().getReference("Order");
        order.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    currentOrder = dataSnapshot.getValue(Tracker.class);
                    if (currentOrder.getStatus().equals("on journey")) {
                        inJourney = true;
                        mDestination = new LatLng(currentOrder.getLatitude(), currentOrder.getLongitude());
                        retrieveDisplayData();
                        destinationTV.setText("Destination : " + mDestination.latitude + "," + mDestination.longitude);
                        finishJourneyButton.setEnabled(true);
                    }
                    if (!isServiceRunning(TrackingService.class))
                    {startLocationService();} else{
                        //service never running before, check if the device is supported
                        requestPermission(Manifest.permission.ACCESS_FINE_LOCATION);
                        requestPermission(Manifest.permission.INTERNET);
                        checkPlayServices();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    public void finishJourney(View v) {
        if (isServiceRunning(TrackingService.class)) {
            stopLocationService();
        }
        Map<String, Object> updateStatus = new HashMap<>();
        updateStatus.put("status", "completed");

        order.updateChildren(updateStatus);
        locations.removeValue();
        inJourney = false;
        finishJourneyButton.setEnabled(false);
    }

    /**
     * Setup map
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
    }


    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if GooglePlayService is available
     *
     * @return
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, TrackingActivity.this, GOOGLE_PLAY_RES_REQUEST).show();
            } else {
                Toast.makeText(this, "Device is not supported", Toast.LENGTH_LONG).show();
            }
            return false;
        }
        return true;
    }

    /**
     * Request app permission for API 23/ Android 6.0
     *
     * @param permission
     */
    private void requestPermission(String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission},
                    MY_PERMISSIONS_REQUEST);
        }
    }

    /**
     * Callback for location permission request - if successful, run the GPS check.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[]
            grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show();
                    } else {
                        startLocationService();
                    }
                }
            }
        }
    }

    @TargetApi(23)
    private void startLocationService() {
        // Before we start the service, confirm that we have extra power usage privileges.
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        Intent intent = new Intent();
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
        startService(new Intent(this, TrackingService.class));
    }

    private void stopLocationService() {
        stopService(new Intent(this, TrackingService.class));
    }

    /**
     * Retrieve current location data from Firebase Database & display its routes
     */
    private void retrieveDisplayData() {
        locations.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Tracker data = dataSnapshot.getValue(Tracker.class);
                    mCurrentLocation = new LatLng(data.getLatitude(), data.getLongitude());
                    showMarker(mCurrentLocation, mDestination);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    /**
     * Show pin for two location and its routes
     *
     * @param originlocation
     * @param destinationLocation
     */
    private void showMarker(LatLng originlocation, LatLng destinationLocation) {
        mMap.clear();
        LatLng origin = new LatLng(originlocation.latitude, originlocation.longitude);
        LatLng destination = new LatLng(destinationLocation.latitude, destinationLocation.longitude);
        mMap.addMarker(new MarkerOptions().position(origin).title("Truck"));
        mMap.addMarker(new MarkerOptions().position(destination).title("Destination"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(origin, 8f));
        new TaskDirectionRequest().execute(getRequestedUrl(originlocation, destinationLocation));
    }

    /**
     * Create requested url for Direction API to get routes from origin to destination
     *
     * @param origin
     * @param destination
     * @return
     */
    private String getRequestedUrl(LatLng origin, LatLng destination) {
        String strOrigin = "origin=" + origin.latitude + "," + origin.longitude;
        String strDestination = "destination=" + destination.latitude + "," + destination.longitude;
        String sensor = "sensor=false";
        String mode = "mode=driving";

        String param = strOrigin + "&" + strDestination + "&" + sensor + "&" + mode;
        String output = "json";
        String APIKEY = "&key=AIzaSyBFpDg_BrWVhYdqITtlmUJ5Ed2GMkVmhp4";

        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + param + APIKEY;
        Log.d(TAG, "Urlrequested is created: " + url);
        return url;
    }

    /**
     * Request direction from Google Direction API
     *
     * @param requestedUrl see {@link #getRequestedUrl(LatLng, LatLng)}
     * @return JSON data routes/direction
     */
    private String requestDirection(String requestedUrl) {
        String responseString = "";
        InputStream inputStream = null;
        HttpURLConnection httpURLConnection = null;
        try {
            URL url = new URL(requestedUrl);
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.connect();

            inputStream = httpURLConnection.getInputStream();
            InputStreamReader reader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(reader);

            StringBuffer stringBuffer = new StringBuffer();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                stringBuffer.append(line);
            }
            responseString = stringBuffer.toString();
            bufferedReader.close();
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        httpURLConnection.disconnect();
        return responseString;
    }


    //Get JSON data from Google Direction
    public class TaskDirectionRequest extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strings) {
            String responseString = "";
            try {
                responseString = requestDirection(strings[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return responseString;
        }

        @Override
        protected void onPostExecute(String responseString) {
            super.onPostExecute(responseString);
            TaskParseDirection parseResult = new TaskParseDirection();
            parseResult.execute(responseString);
        }
    }

    //Parse JSON Object from Google Direction API & display it on Map
    public class TaskParseDirection extends AsyncTask<String, Void, List<List<HashMap<String, String>>>> {
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonString) {
            List<List<HashMap<String, String>>> routes = null;
            JSONObject jsonObject = null;

            try {
                jsonObject = new JSONObject(jsonString[0]);
                DirectionParser parser = new DirectionParser();
                routes = parser.parse(jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> lists) {
            super.onPostExecute(lists);
            ArrayList points = null;
            PolylineOptions polylineOptions = null;

            for (List<HashMap<String, String>> path : lists) {
                points = new ArrayList();
                polylineOptions = new PolylineOptions();

                for (HashMap<String, String> point : path) {
                    double lat = Double.parseDouble(point.get("lat"));
                    double lon = Double.parseDouble(point.get("lng"));

                    points.add(new LatLng(lat, lon));
                }
                polylineOptions.addAll(points);
                polylineOptions.width(15f);
                polylineOptions.color(Color.BLUE);
                polylineOptions.geodesic(true);
            }
            if (polylineOptions != null) {
                mMap.addPolyline(polylineOptions);
            } else {
                Toast.makeText(getApplicationContext(), "Direction not found", Toast.LENGTH_LONG).show();
            }
        }
    }
}
