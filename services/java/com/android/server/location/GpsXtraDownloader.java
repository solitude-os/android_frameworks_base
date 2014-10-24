/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.location;

import android.content.Context;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.URL;
import libcore.io.IoUtils;
import libcore.io.Streams;

import java.io.IOException;
import java.util.Properties;
import java.util.Random;

/**
 * A class for downloading GPS XTRA data.
 *
 * {@hide}
 */
public class GpsXtraDownloader {

    private static final String TAG = "GpsXtraDownloader";
    static final boolean DEBUG = false;

    private final String[] mXtraServers;
    // to load balance our server requests
    private int mNextServerIndex;

    GpsXtraDownloader(Properties properties) {
        // read XTRA servers from the Properties object
        int count = 0;
        String server1 = properties.getProperty("XTRA_SERVER_1");
        String server2 = properties.getProperty("XTRA_SERVER_2");
        String server3 = properties.getProperty("XTRA_SERVER_3");
        if (server1 != null) count++;
        if (server2 != null) count++;
        if (server3 != null) count++;
        
        if (count == 0) {
            Log.e(TAG, "No XTRA servers were specified in the GPS configuration");
            mXtraServers = null;
        } else {
            mXtraServers = new String[count];
            count = 0;
            if (server1 != null) mXtraServers[count++] = server1;
            if (server2 != null) mXtraServers[count++] = server2;
            if (server3 != null) mXtraServers[count++] = server3;

            // randomize first server
            Random random = new Random();
            mNextServerIndex = random.nextInt(count);
        }       
    }

    byte[] downloadXtraData() {
        byte[] result = null;
        int startIndex = mNextServerIndex;

        if (mXtraServers == null) {
            return null;
        }

        // load balance our requests among the available servers
        while (result == null) {
            result = doDownload(mXtraServers[mNextServerIndex]);
            
            // increment mNextServerIndex and wrap around if necessary
            mNextServerIndex++;
            if (mNextServerIndex == mXtraServers.length) {
                mNextServerIndex = 0;
            }
            // break if we have tried all the servers
            if (mNextServerIndex == startIndex) break;
        }
    
        return result;
    }

    protected static byte[] doDownload(String url) {
        if (DEBUG) Log.d(TAG, "Downloading XTRA data from " + url);

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) (new URL(url)).openConnection();
            connection.setRequestProperty(
                    "Accept",
                    "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic");
            connection.setRequestProperty(
                    "x-wap-profile",
                    "http://www.openmobilealliance.org/tech/profiles/UAPROF/ccppschema-20021212#");

            connection.connect();
            int statusCode = connection.getResponseCode();
            if (statusCode != HttpURLConnection.HTTP_OK) {
                if (DEBUG) Log.d(TAG, "HTTP error downloading gps XTRA: " + statusCode);
                return null;
            }

            return Streams.readFully(connection.getInputStream());
        } catch (IOException ioe) {
            if (DEBUG) Log.d(TAG, "Error downloading gps XTRA: ", ioe);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

}
