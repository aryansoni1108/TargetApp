package com.andreasgift.targetapp;

/**
 * Object data to be sent to Firebase Database
 * Note : This class should be identical with Host App
 */
public class Tracker {

    private String vehicleId;
    private String status;
    private double latitude;
    private double longitude;

    public String getStatus() {
        return status;
    }

    public Tracker(String mId, String status, double latitude, double longitude) {
        this.vehicleId = mId;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Tracker(){}

    public String getVehicleId() {
        return vehicleId;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}

