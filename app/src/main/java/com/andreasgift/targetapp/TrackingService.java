package com.andreasgift.targetapp;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class TrackingService extends Service implements LocationListener {
    private static final String TAG = "TrackingService";
    private static final String VEHICLE_ID = "Truck01";

    private DatabaseReference locations; //contain target data
    private DatabaseReference order;

    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;
    private LatLng mCurrentLocation;
    private LocationCallback locationCallback;
    private Tracker mCurentOrder;

    private final long UPDATE_INTERVAL = 1*30*1000;
    private final long FASTEST_INTERVAL = 1*30*1000;
    private final long DISTANCE = 10;

    @Override
    public void onCreate() {
        super.onCreate();
        locations = FirebaseDatabase.getInstance().getReference("Locations");
        order = FirebaseDatabase.getInstance().getReference("Order");

        order.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mCurentOrder = dataSnapshot.getValue(Tracker.class);
                if (mCurentOrder != null && mCurentOrder.getStatus().equals("on journey")){
                    startJourney();
                    Log.d(TAG, "startJourney is called");
                } else {
                    Log.d(TAG, "Destination not found");
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) { }
        });
        locationCallback = new LocationCallback(){
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                mCurrentLocation = new LatLng(locationResult.getLastLocation().getLatitude(), locationResult.getLastLocation().getLongitude());
                locations.setValue(new Tracker(VEHICLE_ID, "on journey", mCurrentLocation.latitude, mCurrentLocation.longitude));
                Log.d(TAG, "Location updates is sent to Firebase Database");
            }
        };
        buildGoogleAPiClient();
    }

    @Override
    public void onDestroy() {
        if (mGoogleApiClient != null) {
            LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(
                    locationCallback
            );
        }
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = new LatLng(location.getLatitude(), location.getLongitude());
        sendLocationtoDatabase();
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    public void startJourney(){
        sendLocationtoDatabase();
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationRequest.setSmallestDisplacement(DISTANCE);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        Log.d(TAG, "Location request is created");

        LocationServices.getFusedLocationProviderClient(this).
                requestLocationUpdates(mLocationRequest,locationCallback,null);
    }

    /**
     * Send current location data to Firebase
     */
    private void sendLocationtoDatabase() {
        LocationServices.getFusedLocationProviderClient(this).getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        mCurrentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        locations.setValue(new Tracker(VEHICLE_ID, "on journey",mCurrentLocation.latitude, mCurrentLocation.longitude));
                        Log.d(TAG, "Origin location is sent to Firebase Database");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        e.printStackTrace();
                    }
                });
    }


    private GoogleApiClient.ConnectionCallbacks mGoogleClientCallback = new GoogleApiClient.ConnectionCallbacks() {
        @Override
        public void onConnected(@Nullable Bundle bundle) {
            sendLocationtoDatabase();
        }

        @Override
        public void onConnectionSuspended(int i) { mGoogleApiClient.connect(); }
    };


    /**
     * Build Google API Client
     */
    private void buildGoogleAPiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(mGoogleClientCallback)
                .addApi(LocationServices.API).build();
        mGoogleApiClient.connect();
        Log.d(TAG, "GoogleApiClient is connected");
    }

}
