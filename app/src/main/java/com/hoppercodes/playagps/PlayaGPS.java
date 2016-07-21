package com.hoppercodes.playagps;

import android.content.pm.PackageInfo;

import processing.core.*;
import processing.data.*;
import processing.event.*;
import processing.opengl.*;

import ketai.sensors.*;

import java.lang.Math.*;

import java.util.HashMap;
import java.util.ArrayList;
import java.io.File;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public class PlayaGPS extends PApplet {

// PGPS - Playa GPS -
// written in Processing 3.0 June 2016 as first release for 2016
// Joe Miller (PocoJoe)
// redux of PlayaCompass, written to display present position and heading in very large letters for daytime use
// July 2, 2016  - Laura's Engagement weekend
// keystore for Andrew 2016Burn
// permissions need to be set:  Fine location

// ketai declares


    KetaiLocation location;
    KetaiSensor sensor;

    // my global declares
    Table ptable;    // contains persistent navigation values
    PGPS mcl = new PGPS();    // my current location, above needs to be changed to use this class
    LatLonLen lll;  // determines feet per degree of lat and lon at current latitude
    Navigate p;    // navigation vector

    // determine the "golden spike" position and associated metrics
    float GSLAT = 0;      // this is declaration; keep value 0
    float GSLON = 0;      // assign value in SETUP
    float GSTNAD = 315;   // Golden Spike to True North Angle, Degrees:  Must be positive, between 0 and <360 degrees
    float GSDEC = 13.5f;     // Declination angle (magnetic vs true north) at Golden Spike Lat and Lon, about 13.5 degrees east for Black Rock City, about 9 east for Tucson
    float FPDLAT = 0;  // 364336 feet per degree change in latitude is motion along longitude,  vertical motion (y axis) on map
    float FPDLON = 0;    // use this value for black rock city - latitude 40.787063; for Tucson the value is 309173, it is further south.


// create object mcl, my current location

    PVector magneticField, accelerometer;
    float azimuth, pitch, roll;
    float[] matrixR = new float[9];
    float[] matrixI = new float[9];
    float[] matrixValues = new float[9];

    // colors used
    int BLACK = color(0, 0, 0);
    int WHITE = color(255, 255, 255);


    // STARTUPLAT and STARTUPLON records the location where GPS first locked on program start  - this should ALWAYS be initialized to 0
    float STARTUPLAT = 0;
    float STARTUPLON = 0;

    // 2016 (Davinci's Workshop)
    float GSLAT16 = 40.7864f;
    float GSLON16 = -119.2065f;

    // Tucson (bookmans) location for golden spike
    float TUCLAT = 32.250018f;
    float TUCLON = -110.944092f;

    // Burning Man Headquarters
    float BMHQLAT = 37.760949f;
    float BMHQLON = -122.412519f;

///////////////////////////////////////

//
// Assign Golden Spike in startNav, prior to layout being called

    float P1MDF = 8160;  // P1 Man Distance, Feet

// radial street distances from esplanade; 200 feet deep blocks with 20 ft wide roads, but measured (google earth) is 235

    float SMDF = 2500;    // eSplanade center, feet, from man
    float AMDF = 2940;    // A center
    float BMDF = 3180;    // B center
    float CMDF = 3420;    // C center
    float DMDF = 3660;    // D center
    float EMDF = 3900;    // E center
    float FMDF = 4140;    // F center
    float GMDF = 4380;    // G center
    float HMDF = 4620;    // H center
    float IMDF = 4860;    // I center
    float JMDF = 5100;    // J center
    float KMDF = 5340;    // K center
    float LMDF = 5580;    // L center
    float RSCAD = 60;     // Radial Street Starting Clock Angle Degrees
    float RECAD = 300;    // Radial Street Ending Clock Angle Degrees

//*********************************************************************************************************************************************************************
// Global decs above here
// the two big guys (setup() and draw() below here
//*********************************************************************************************************************************************************************

    public void setup()    // setup is called whenever activity comes back to the foreground!
    {

        println("setup() entry:");
        startNav();    // initialize stored navigation values, including golden spike lat, lon
        startCompass();
        startGPS();
        orientation(PORTRAIT);
        frameRate(10);
        PApplet.println("setup(): exit");
    }

    public void draw() {
        // println("draw(): entry");
        readCompass();
        if (mcl.gpsLock) {
            drawDisplay();
        } else {
            drawWaitForGPSLock();
        }
        // println("draw(): exit");
    }

//////////////////////////////////////////////////////////////

    public void drawDisplay() {

        int versionCode = BuildConfig.VERSION_CODE;
        String versionName = BuildConfig.VERSION_NAME;
        int lh = height / 16;
        int lw = width;

        background(255, 255, 255);
        fill(0, 0, 0);
        rectMode(CORNER);
        textAlign(CENTER, CENTER);
        textSize(PApplet.parseInt(lh * 0.4f));
        // text("PlayaGPS 2016.0 Andrew's BMHQ Demo", 0, 0, lw, lh);
        text("PlayaGPS " + versionName + "." + str(versionCode) + " - Da Vinci's Workshop", 0, 0, lw, lh);
        textSize(lh * .75f);
        text(mcl.roseWind, 0, 1.1f * lh, lw, lh); // lh * 1 - first line
        textSize(lh * 3);
        text(mcl.roseHeading, 0, 1.7f * lh, lw, 3.5f * lh);  // eg NNW
        textSize(lh * 3);
        text(mcl.addra, 0, 5 * lh, lw, 3.5f * lh); // eg 4:45
        textSize(lh * 3);
        text(mcl.addrd, 0, 8.3f * lh, lw, 3.5f * lh);
        textSize(lh * 0.75f);
        text(mcl.addrs, 0, 11.5f * lh, lw, lh);
        fill(0, 0, 0);
        rect(lh * 0.2f, 12.8f * lh, lw - 0.4f * lh, 3.0f * lh, lh * 0.1f);
        fill(0, 0, 0);
        textSize(lh * 0.25f);
        text("Tap Gray Box to Mark Location", 0.1f * lw, 12.5f * lh, 0.8f * lw, 0.3f * lh);
        fill(64, 64, 64);  // little gray box showing where to poke
        rect((int) (0.40f * displayWidth), (int) (0.80f * displayHeight), (int) (0.20f * displayWidth), (int) (0.10f * displayHeight));
        fill(255, 255, 255);
        textSize(lh);
        text(p.ndl1, 0.1f * lh, 12.9f * lh, lw - 0.1f * lh, lh);
        textSize(lh * 0.7f);
        text(p.ndl2, 0.1f * lh, 13.9f * lh, lw - 0.1f * lh, lh);
        text(p.ndl3, 0.1f * lh, 14.8f * lh, lw - 0.1f * lh, lh);
    }

    public void drawDisplayDebug() {
        background(WHITE);
        fill(BLACK);
        textSize(height / 30);
        text(
                "Playa GPS (PGPS) 2016 \n\n " +
                        "My Lat/lon:    " + mcl.lat + ", " + mcl.lon + "\n" +
                        "FPD Lat/Lon:    " + (int) FPDLAT + ", " + (int) FPDLON + "\n" +
                        "Accuracy:  " + mcl.accuracy + "\n" +
                        "locProvider:  " + mcl.locProvider + "\n\n" +
                        "Man Lat:   " + GSLAT + "\n" +
                        "Man Lon:   " + GSLON + "\n\n" +
                        "Heading(M):" + (int) mcl.headingM + "\n" +
                        "Heading(N):" + (int) mcl.headingN + "\n" +
                        "Rose Int:  " + mcl.roseInt + "\n" +
                        "Rose Heading:" + mcl.roseHeading + "\n" +
                        "Rose Wind: " + mcl.roseWind + "\n\n" +
                        "Man Deg:   " + mcl.mcads + " deg\n" +
                        "Man Time:  " + mcl.mcats + "\n" +
                        "Man Dist:  " + (int) mcl.mdf + "\n\n" +
                        "ADDRA:     " + mcl.addra + "\n" +
                        "ADDRD:     " + mcl.addrd
                , 0, 0, width, height);
    }

    public void drawWaitForGPSLock() {
        println("drawWaitForGPSLock: called");
        background(BLACK);
        stroke(WHITE);
        fill(WHITE);
        textAlign(CENTER, CENTER);
        textSize((int) (height * 0.06f));
        text(
                "PlayaGPS\n\n" +
                        "Waiting for GPS\n\n" +
                        "Be sure GPS is ON\n\n" +
                        "Check Settings:\n" +
                        "Location Services\n" +
                        "ON"
                , width / 2, height / 2);
    }

    //*********************************************************************************************************************************************************************
// Setup () and Draw() above here
// Functions GPS and Compass next
//*********************************************************************************************************************************************************************
@Override
    public void onResume() {
        super.onResume();
        startNav();
        startCompass();
        startGPS();
    }
@Override
    public void onPause() {
        super.onPause();
        //stopCompass();  // this breaks it
        stopGPS();
    }

    @Override
    public void onStop() {
        super.onStop();
        //stopCompass();  // this breaks it
        stopGPS();
    }

/*
@Override
    public void onStop() {      // when PlayaCompass goes off the screen, stop listening to the GPS to conserve power
        super.onStop();    // apparently onResume is not needed as setup is called whenever app comes to the foreground and location.start is present in setup()
        stopGPS();
        stopCompass();
    }
*/
    public void startCompass() {
        println("startCompass: starting sensor service");
        sensor = new KetaiSensor(this);
        sensor.start();
        sensor.list();
        accelerometer = new PVector();
        magneticField = new PVector();
    }

    /*
    public void stopCompass() {
        println("stopCompass: stopping sensor service");
        if (sensor.isStarted()) {
            try {
                sensor.stop();
            } catch (Exception e) {
                PApplet.println("Error in stopping Compass:"
                        + e.getMessage());
                e.printStackTrace();
            }
        }
        sensor = null;
    }
    */

    public void startNav() {
        println("startNav: loading Navigtion data:");
        GSLAT = GSLAT16;  // 2016 and 2015
        GSLON = GSLON16;  // are the same lat
        // GSLAT = TUCLAT;  // tucson bookmans lat
        // GSLON = TUCLON;  // tucson bookmans lon
        // GSLAT = BMHQLAT;
        // GSLON = BMHQLON;
        lll = new LatLonLen(GSLAT, GSLON);
        FPDLAT = lll.latlenF;
        FPDLON = lll.lonlenF;
        p = new Navigate(GSLAT, GSLON, GSLAT, GSLON, FPDLAT, FPDLON);
        p.start();
        p.update();
    }

    public void startGPS() {
        println("startGPS: starting Location service and GPS receiver");
        location = new KetaiLocation(this);
        mcl.gpsLock = false;
        mcl.setLatitude(STARTUPLAT);
        mcl.setLongitude(STARTUPLON);
        location.setUpdateRate(1000, 10);
        location.start();
    }

    public void stopGPS() {
        println("stopGPS: stopping location service and buffering Nav data");
        p.update(); // buffer the nav data
        p.persist();    // first write down where things were
        println("stopGPS: stop listening, buffer navigation goal");
        if (location.isStarted()) {
            try {
                location.stop();
            } catch (Exception e) {
                PApplet.println("Error in PlayaCompass onPause:"
                        + e.getMessage());
                e.printStackTrace();
            }
        }
        location = null;
    }

    public void onLocationEvent(Location location) {
        String provider;

        try {
            provider = location.getProvider();
        } catch (Exception e) {
            println(e.toString());
            e.printStackTrace();
            provider = "nope";
            println("nyet! no gps for you!");
        }
        provider = provider.toLowerCase();
        mcl.setlocProvider(provider);
        if (provider.equals("gps")) {
            println("onLocationEvent: gps update recieved");
            mcl.gpsLock = true;
            mcl.setLatitude((float) location.getLatitude());
            mcl.setLongitude((float) location.getLongitude());
            mcl.setAccuracy((float) location.getAccuracy());
            mcl.update();  // update current location
            p.fromlat = mcl.lat;  // update the nav calculations
            p.fromlon = mcl.lon;  // with lat and lon
            p.update();    // from current location
            p.persist();   // save the data regarding the marked spot for onResume
        } else {
            mcl.gpsLock = false;
            println("onLocationEvent: getting updates, but not from GPS");
            drawWaitForGPSLock();
        }
    }

    public void mousePressed() {
        int ulx = (int) (0.40f * displayWidth);
        int lrx = (int) (0.60f * displayWidth);

        int uly = (int) (0.80f * displayHeight);
        int lry = (int) (0.90f * displayHeight);

        if (((mouseX > ulx) && (mouseX < lrx)) && ((mouseY > uly) && (mouseY < lry))) {
            p.mlat = mcl.lat;        // latitude for marked location
            p.mlon = mcl.lon;        // longitude for marked location
            p.mmcats = mcl.mcats;  // man clock angle time string for marked location
            p.mmdf = mcl.mdf;      // man distance, feet for marked location
            p.mday = day();
            p.mmonth = month();
            p.myear = year();
            p.mhour = hour();
            p.mminute = minute();
            p.msecond = second();
            p.update();
            p.persist();
        }
    }


    public void readCompass() {
        int rerror, target;    // compass rose error, target selects new rose values
        float headingM = 0;
        float headingN = 0;
        try {
            boolean success = android.hardware.SensorManager.getRotationMatrix(
                    matrixR,
                    matrixI,
                    accelerometer.array(),
                    magneticField.array());
            if (success) {
                android.hardware.SensorManager.getOrientation(matrixR, matrixValues);
                azimuth = matrixValues[0];
                pitch = matrixValues[1];
                roll = matrixValues[2];
                headingM = degrees(azimuth);
                if (headingM < 0) headingM = headingM + 360;
                if (headingM > 360) headingM = headingM - 360;
            }
        } catch (Exception e) {
            PApplet.println("Error in PlayaCompass onPause:"
                    + e.getMessage());
            e.printStackTrace();
            // PApplet.println("Error: " + e);
        }
        mcl.setHeadingM(headingM);
        headingN = headingM - GSDEC;  // mag dec is to the east.  mag reading, minus dec, is true north.
        if (headingN < 0) headingN = headingN + 360;
        if (headingN > 360) headingN = headingN - 360;
        mcl.setHeadingN(headingN);
        rerror = 180 - abs((abs(mcl.roseInt - (int) headingN)) - 180);
        // angle error = 180-abs(abs(a1-a2)-180)  thanks some guy on stack overflow!
        // using a 16 point rose; 360/16=22.5; half would be 11.25
        // estimating 2 SD to be 3.75 degrees so bounding the 11.25 + 3.75 = 15 degrees.
        if (rerror > 15) {
            // the heading has "jumped" from the constrained heading by more than allowed by hysteresis
            // determine the new target heading as 1 of 16 possible rose positions
            target = (PApplet.parseInt((headingN + 11.25f) / 22.5f)) % 16;
            setRose(target);
        }
    }

    public void setRose(int target) {
        switch (target) {
            case 0:
                mcl.roseInt = 360;      // apparently pilots use 360 and sailors use 0 to indicate north
                mcl.roseHeading = "N";
                mcl.roseWind = "Tramontano";
                break;
            case 1:
                mcl.roseInt = 23;
                mcl.roseHeading = "NNE";
                mcl.roseWind = "Greco-Tramantano";
                break;
            case 2:
                mcl.roseInt = 45;
                mcl.roseHeading = "NE";
                mcl.roseWind = "Greco";
                break;
            case 3:
                mcl.roseInt = 68;
                mcl.roseHeading = "ENE";
                mcl.roseWind = "Greco-Levante";
                break;
            case 4:
                mcl.roseInt = 90;
                mcl.roseHeading = "E";
                mcl.roseWind = "Levante";
                break;
            case 5:
                mcl.roseInt = 113;
                mcl.roseHeading = "ESE";
                mcl.roseWind = "Levante-Scirocco";
                break;
            case 6:
                mcl.roseInt = 135;
                mcl.roseHeading = "SE";
                mcl.roseWind = "Scirocco";
                break;
            case 7:
                mcl.roseInt = 158;
                mcl.roseHeading = "SSE";
                mcl.roseWind = "Ostro-Scirocco";
                break;
            case 8:
                mcl.roseInt = 180;
                mcl.roseHeading = "S";
                mcl.roseWind = "Ostro";
                break;
            case 9:
                mcl.roseInt = 203;
                mcl.roseHeading = "SSW";
                mcl.roseWind = "Ostro-Libeccio";
                break;
            case 10:
                mcl.roseInt = 225;
                mcl.roseHeading = "SW";
                mcl.roseWind = "Libeccio";
                break;
            case 11:
                mcl.roseInt = 248;
                mcl.roseHeading = "WSW";
                mcl.roseWind = "Ponente-Libeccio";
                break;
            case 12:
                mcl.roseInt = 270;
                mcl.roseHeading = "W";
                mcl.roseWind = "Ponente";
                break;
            case 13:
                mcl.roseInt = 293;
                mcl.roseHeading = "WNW";
                mcl.roseWind = "Maestro-Ponente";
                break;
            case 14:
                mcl.roseInt = 315;
                mcl.roseHeading = "NW";
                mcl.roseWind = "Maestro";
                break;
            case 15:
                mcl.roseInt = 338;
                mcl.roseHeading = "NNW";
                mcl.roseWind = "Maestro-Tramontana";
                break;
            default:
                //oops!
                break;
        }
    }

    public void onAccelerometerEvent(float x, float y, float z, long time, int accuracy) {
        accelerometer.set(x, y, z);
    }

    public void onMagneticFieldEvent(float x, float y, float z, long time, int accuracy) {
        magneticField.set(x, y, z);
    }


//************************************************************************************************************************************************************
// Setup, Draw, GPS Above
// Compass below
//************************************************************************************************************************************************************

    class PGPS {        // PlayaGPS:  convert a position from man polar to a point on the display
        // accesses sensors and location manager to determine present position in playa coordinates and heading
        boolean gpsLock = false;      // most recent reading as to whether the sourse of location is gps or network;
        float lat;      // latitude, degrees and decimal fraction
        float lon;      // longitude, degrees and azimuth fraction
        float accuracy;  // estimate of how (in)accurately lat and lon is known-- more likely resolution  (from gps, else 0)
        float headingM;  // compass heading (magnetic)
        float headingN;  // heading (true North)
        float mdf;      // man polar distance, feet, from golden spike under man, using flat earth exact feet per degreee lat/lon
        float mdmi;      // man distance, miles, computed using Haversine formula and mean earth radius
        float mcad;      // man clock angle, degrees, polar from 12:00 straight "up"

        int mcah;        // man clock angle, Hours: 0 to 360 degrees maps from 00:00 to 11:59:59, allowing 1 ft resolution
        int mcam;        // man clock angle, Minutes
        int mcas;        // man clock angle, Seconds
        String mcads;    // man clock angle degree string (truncated to d.d)
        String mcats;     // man clock angle time string: HH:MM string comprised of above
        String addra;     // address - angle - HH:MM or Deg
        String addrd;      // address distance - either feet or miles
        String addrs;      // address street - a longer string, printed smaller
        String locProvider = "";    // network or gps
        String roseHeading = "";      // compass rose value
        String roseWind = "";
        int roseInt = 0;

        // constructor; accepts polar coordinates for location, and indication if it should be displayed distance from man, feet: angle from man
        // the lat/lon, pixel coordinates, and time angle are assigned and do not change; however px and py are updated when update is called.
        // a method is provided for constructing with polar, but then updating location with gps lat/lon for moving objects

        PGPS() {    //new void constructor
        }

        public void setLatitude(float tlatitude) {
            this.lat = tlatitude;
        }

        public void setLongitude(float tlongitude) {
            this.lon = tlongitude;
        }

        public void setAccuracy(float taccuracy) {
            this.accuracy = taccuracy;
        }

        public void setlocProvider(String tlocProvider) {
            this.locProvider = tlocProvider;
        }

        public void setHeadingM(float theadingM) {
            this.headingM = theadingM;
        }

        public void setHeadingN(float theadingN) {
            this.headingN = theadingN;
        }

        public float haversine(float lat1, float lon1, float lat2, float lon2) {
            // https://rosettacode.org/wiki/Haversine_formula#Java
            // result is in kilometers;, convert to miles at bottom
            float R = 6371;    // mean radius of earth - which is not a sphere.  Pretty close
            float dLat = (float) Math.toRadians((double) (lat2 - lat1));
            float dLon = (float) Math.toRadians((double) (lon2 - lon1));
            lat1 = (float) Math.toRadians((double) lat1);
            lat2 = (float) Math.toRadians((double) lat2);

            float a = (float) (Math.pow(Math.sin(dLat / 2), 2) + Math.pow(Math.sin(dLon / 2), 2) * Math.cos(lat1) * Math.cos(lat2));
            float c = (float) (2 * Math.asin(Math.sqrt(a)));
            return (R * c) * 0.621371f;    // radius is in km so result is km till converted to miles
        }

        public void update() {  // include the reading from the compass MYHEADING
            String street = "";
            float dxf = (this.lon - GSLON) * FPDLON;
            float dyf = (this.lat - GSLAT) * FPDLAT;
            this.mdf = sqrt(dxf * dxf + dyf * dyf);
            this.mcad = ((360 - (degrees(atan2(dyf, dxf))) + 90) + GSTNAD);   // the (360- reverses the rotation from CCW to CW; the -90 rotates 0 from along +x to +y, and - GSNAD rotates the 45 degrees man is rotated to right from true north
            this.mcad = ((this.mcad + 360) % 360);  // normalize the angle to 0 to 360
            this.mcads = String.format("%.1f", this.mcad);
            this.mcas = (int) (this.mcad * 120);  // there are 360 degrees in 12*60*60 seconds, so each degree is 360/(12*60*60) seconds or 120 seconds
            this.mcah = this.mcas / (3600);
            this.mcam = (this.mcas - this.mcah * 3600) / 60;
            this.mcas = this.mcas - this.mcah * 3600 - this.mcam * 60;
            this.mcats = String.format("%d", this.mcah) + ":" + String.format("%02d", this.mcam);
            if (this.mdf < SMDF - 20) street = "Inner Playa";
            if (this.mdf >= SMDF - 20) street = "Esplanade";
            if (this.mdf >= SMDF + 20) street = "Esp-A Block";
            if (this.mdf >= AMDF - 20) street = "Arno";
            if (this.mdf >= AMDF + 20) street = "A-B Block";
            if (this.mdf >= BMDF - 20) street = "Botticelli";
            if (this.mdf >= BMDF + 20) street = "B-C Block";
            if (this.mdf >= CMDF - 20) street = "Cosimo";
            if (this.mdf >= CMDF + 20) street = "C-D Block";
            if (this.mdf >= DMDF - 20) street = "Donatello";
            if (this.mdf >= DMDF + 20) street = "D-E Block";
            if (this.mdf >= EMDF - 20) street = "Effigiare";
            if (this.mdf >= EMDF + 20) street = "E-F Block";
            if (this.mdf >= FMDF - 20) street = "Florin";
            if (this.mdf >= FMDF + 20) street = "F-G Block";
            if (this.mdf >= GMDF - 20) street = "Guild";
            if (this.mdf >= GMDF + 20) street = "G-H Block";
            if (this.mdf >= HMDF - 20) street = "High Ren";
            if (this.mdf >= HMDF + 20) street = "H-I Block";
            if (this.mdf >= IMDF - 20) street = "Italic";
            if (this.mdf >= IMDF + 20) street = "I-J Block";
            if (this.mdf >= JMDF - 20) street = "Justice";
            if (this.mdf >= JMDF + 20) street = "J-K Block";
            if (this.mdf >= KMDF - 20) street = "Knowledge";
            if (this.mdf >= KMDF + 20) street = "K-L Block";
            if (this.mdf >= LMDF - 20) street = "Lorenzo";
            if (this.mdf > LMDF + 20) street = "Perimeter";

            this.addra = this.mcats;  // HH:MM
            this.addrd = Integer.toString((int) this.mdf);
            if ((this.mcad >= RSCAD) && (this.mcad <= RECAD)) {
                this.addrs = street;
            } else {
                this.addrs = "Playa";
            }
            if ((this.mdf) > P1MDF) {  // outside trash fence; give distance in miles
                {
                    this.mdmi = this.haversine(GSLAT, GSLON, this.lat, this.lon);
                    this.addra = "BRC";
                    this.addrd = String.format("%.0f", (this.mdmi)) + "mi";
                    this.addrs = "";
                }
            }
        }
    }


    public class Navigate {      // navigate from current location (here) to point there (established at some time by user tapping control)
        int mday = 0;  // mark dae
        int mmonth = 0;  // mark month
        int myear = 0;
        int mhour = 0;
        int mminute = 0;
        int msecond = 0;
        float mlat = 0;        // mark (destination) latitude (where I am going to)
        float mlon = 0;        // mark (destination) longitude
        float mmdf = 0;        // mark (destination) man distance; feet
        float fromlat = 0;      // current location (where I am coming from)
        float fromlon = 0;
        float fpdlat = 0;        // feet per degree lat and lon; determined in setup
        float fpdlon = 0;
        float ndf = 0;         // navigation (current to destination) distance, feet
        float nbnd = 0;        // nav,  bearing true north degrees
        String nbnrose = "ROSE";   // navigation bearing, true north compass rose (eg NNW)
        String mmcats = "0:00";           // mark (destination) man clock angle time string
        String ndl1 = "Nav Line 1";      // Nav Display Line 1
        String ndl2 = "Nav Line 2";      // Nav Display Line 2
        String ndl3 = "Nav Line 3";      // Nav Display Line 3

        Navigate(float flat, float flon, float tlat, float tlon, float tfpdlat, float tfpdlon) {
            fromlat = flat;
            fromlon = flon;
            mlat = tlat;
            mlon = tlon;
            fpdlat = tfpdlat;
            fpdlon = tfpdlon;
        }

        public void start() {
            // load stored values for navigation.  These must be buffered
            // as when an activity loses focus, the values are lost.
            // a processing table is used to store the values.
            // the table has only one line.
            // if the table does not exist (as would occur on install),
            // it is created.
            // navigate object p contains these values.
            try {
                ptable = loadTable("ptable.csv", "header");
            } catch (Exception e) {
                println(e.toString());
                println("startNav: table of initial values not present.  ptable set to null");
                ptable = null;
            }
            if (ptable == null) {    // create table here
                println("\n");
                println("\n");
                println("startNav: initializing table pgstable.csv with header");
                println("creating table ptable");
                ptable = new Table();
                ptable.addColumn("mday");
                ptable.addColumn("mmonth");
                ptable.addColumn("myear");
                ptable.addColumn("mhour");
                ptable.addColumn("mminute");
                ptable.addColumn("msecond");
                ptable.addColumn("mlat");
                ptable.addColumn("mlon");
                ptable.addColumn("mmdf");
                ptable.addColumn("mmcats");
                TableRow row = ptable.addRow();
                row.setInt("mday", day());
                row.setInt("mmonth", month());
                row.setInt("myear", year());
                row.setInt("mhour", hour());
                row.setInt("mminute", minute());
                row.setInt("msecond", second());
                row.setFloat("mlat", 0f);
                row.setFloat("mlon", 0f);
                row.setFloat("mmdf", 0f);
                row.setString("mmcats", "00:00");
                p.persist();
                println("   ptable created");
            } else {
                // load the values from the table
                println("INITIALIZING NAV SYSTEM FROM SAVED VALUES");
                println("copying values from ptable into nav");
                mday = ptable.getInt(0, "mday");
                mmonth = ptable.getInt(0, "mmonth");
                myear = ptable.getInt(0, "myear");
                mhour = ptable.getInt(0, "mhour");
                mminute = ptable.getInt(0, "mminute");
                msecond = ptable.getInt(0, "msecond");
                mlat = ptable.getFloat(0, "mlat");
                mlon = ptable.getFloat(0, "mlon");
                mmdf = ptable.getFloat(0, "mmdf");
                mmcats = ptable.getString(0, "mmcats");
                println("   NAV SYSTEM INITIALIZED");
                p.update();
            }
        }

        public void persist() {
            println("saving table ptable.csv");
            ptable.setInt(0, "mday", mday);
            ptable.setInt(0, "mmonth", mmonth);
            ptable.setInt(0, "myear", myear);
            ptable.setInt(0, "mhour", mhour);
            ptable.setInt(0, "mminute", mminute);
            ptable.setInt(0, "msecond", msecond);
            ptable.setFloat(0, "mlat", mlat);
            ptable.setFloat(0, "mlon", mlon);
            ptable.setFloat(0, "mmdf", mmdf);
            ptable.setString(0, "mmcats", mmcats);
            saveTable(ptable, "ptable.csv", "csv");
            println("ptable updated and saved");
        }

        public void update() {
            double dxf = 0;     // east-west navigation vector in feet
            double dyf = 0;     // north-south navigation vector in feet
            double ucad = 0;    // unit circle angle, degrees

            dxf = (double) (this.fromlon - this.mlon) * this.fpdlat;
            dyf = (double) (this.fromlat - this.mlat) * this.fpdlon;
            this.ndf = (float) Math.sqrt(dxf * dxf + dyf * dyf);

            ucad = (Math.toDegrees(Math.atan2(dyf, dxf)));
            this.nbnd = (float) ((((360 - ucad) - 90) + 360) % 360);
            if (this.nbnd >= 0) this.nbnrose = "N";
            if (this.nbnd >= 23) this.nbnrose = "NNE";
            if (this.nbnd >= 45) this.nbnrose = "NE";
            if (this.nbnd >= 68) this.nbnrose = "ENE";
            if (this.nbnd >= 90) this.nbnrose = "E";
            if (this.nbnd >= 113) this.nbnrose = "ESE";
            if (this.nbnd >= 135) this.nbnrose = "SE";
            if (this.nbnd >= 158) this.nbnrose = "SSE";
            if (this.nbnd >= 180) this.nbnrose = "S";
            if (this.nbnd >= 203) this.nbnrose = "SSW";
            if (this.nbnd >= 225) this.nbnrose = "SW";
            if (this.nbnd >= 248) this.nbnrose = "WSW";
            if (this.nbnd >= 270) this.nbnrose = "W";
            if (this.nbnd >= 293) this.nbnrose = "WNW";
            if (this.nbnd >= 315) this.nbnrose = "NW";
            if (this.nbnd >= 338) this.nbnrose = "NNW";
            if (this.nbnd >= 360) this.nbnrose = "N";
            // build the display strings
            this.ndl1 = this.nbnrose + "  " + (int) this.ndf + " ft";
            this.ndl2 = this.mmcats + " & " + trim(String.format("%4.0f", this.mmdf)) + "   " + Integer.toString(this.mmonth) + "/" + Integer.toString(this.mday) + "  " + Integer.toString(this.mhour) + ":" + String.format("%02d", this.mminute);
            this.ndl3 = String.format("%10.6f", this.mlat) + "," + String.format("%10.6f", this.mlon);
        }
    }


    class LatLonLen {  // routine to determine the feet per degree of latitude and longitude for any latitude
        // http://gis.stackexchange.com/questions/75528
        // http://msi.nga.mil/MSISiteContent/StaticFiles/Calculators/degree.html
        // wgs84 elliptical calcs
        float lat;                // latitude at which calculation is made, in radians
        float lon;                // longitude at which calcualtion is made, in radians
        float m1 = 111132.92f;     // latitude calculation term 1
        float m2 = -559.82f;       // latitude calculation term 2
        float m3 = 1.175f;         // latitude calculation term 3
        float m4 = -0.0023f;       // latitude calculation term 4
        float p1 = 111412.84f;     // longitude calculation term 1
        float p2 = -93.5f;         // longitude calculation term 2
        float p3 = 0.118f;         // longitude calculation term 3
        float latlenM;            // meters per degree latitude at target latitude
        float lonlenM;            // meters per degree longitude at target longitude
        float latlenF;            // feet per degree latitude at target latitude
        float lonlenF;            // feet per degree longitude at target longitude

        LatLonLen(float tlatdeg, float tlondeg) {  // target latitude and longitude, in degrees
            this.lat = (float) Math.toRadians(tlatdeg);
            this.lon = (float) Math.toRadians(tlondeg);
            this.latlenM = (float) (m1 + (m2 * Math.cos(2 * this.lat)) + (m3 * Math.cos(4 * this.lat)) + (m4 * Math.cos(6 * this.lat)));
            this.lonlenM = (float) ((p1 * Math.cos(this.lat)) + (p2 * Math.cos(3 * this.lat)) + (p3 * Math.cos(5 * this.lat)));
            this.latlenF = this.latlenM * 3.28084f;
            this.lonlenF = this.lonlenM * 3.28084f;
        }
    }


    // end of file PlayaGPS
    public void settings() {
        size(displayWidth, displayHeight);
    }

    static public void main(String[] passedArgs) {
        String[] appletArgs = new String[]{"PlayaGPS"};
        if (passedArgs != null) {
            PApplet.main(concat(appletArgs, passedArgs));
        } else {
            PApplet.main(appletArgs);
        }
    }
}

// end of file PlayaGPS.java