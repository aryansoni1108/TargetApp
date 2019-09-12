package com.andreasgift.targetapp;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationListener;
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

    private static final String STATUS_ONJOURNEY = "on journey";
    private static final String STATUS_DELIVERED = "delivered";

    private DatabaseReference locations; //contain target data
    private DatabaseReference order;

    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;
    private LatLng mCurrentLocation;
    private LocationCallback locationCallback;
    private Tracker mCurentOrder;

    private final long UPDATE_INTERVAL = 2*60*1000;
    private final long FASTEST_INTERVAL = 2*60*1000;
    private final long DISTANCE = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        locations = FirebaseDatabase.getInstance().getReference("Locations");
        order = FirebaseDatabase.getInstance().getReference("Order");

        buildGoogleAPiClient();

        order.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mCurentOrder = dataSnapshot.getValue(Tracker.class);
                if (mCurentOrder != null && mCurentOrder.getStatus().equals(STATUS_ONJOURNEY)){
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
                locations.setValue(new Tracker(VEHICLE_ID, STATUS_ONJOURNEY, mCurrentLocation.latitude, mCurrentLocation.longitude));
                Log.d(TAG, "Location updates is sent to Firebase Database");
            }
        };
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
                requestLocationUpdates(mLocationRequest,locationCallback, null);
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
                        locations.setValue(new Tracker(VEHICLE_ID, STATUS_ONJOURNEY,mCurrentLocation.latitude, mCurrentLocation.longitude));
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
