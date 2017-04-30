package com.example.android.sunshine.utilities;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.android.sunshine.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

public class WearableHelper {
    
    private static final String TAG = WearableHelper.class.getSimpleName();
    private final GoogleApiClient mGoogleApiClient;
    private Context context;
    
    public WearableHelper(Context context) {
        this.context = context;
        //update weareable datas
        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(context).addApi(Wearable.API);
        builder = builder.addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(Bundle connectionHint) {
                Log.d(TAG, "onConnected: " + connectionHint);
                updateWearable();
            }
            
            @Override
            public void onConnectionSuspended(int cause) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }).addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
            @Override
            public void onConnectionFailed(ConnectionResult result) {
                Log.d(TAG, "onConnectionFailed: " + result);
            }
        });
        mGoogleApiClient = builder.build();
        mGoogleApiClient.connect();
    }
    
    void updateWearable() {
            
            /* Build the URI for today's weather in order to show up to date data in notification */
        Uri todaysWeatherUri = WeatherContract.WeatherEntry
                .buildWeatherUriWithDate(SunshineDateUtils.normalizeDate(System.currentTimeMillis()));
        
        Log.d(TAG, "Quering...");
        ContentResolver cr = context.getContentResolver();
        Cursor todayWeatherCursor = cr.query(todaysWeatherUri, NotificationUtils.WEATHER_NOTIFICATION_PROJECTION, null, null, null);
        
        if (todayWeatherCursor.moveToFirst()) {
            Log.d(TAG, "Find!");
                /* Weather ID as returned by API, used to identify the icon to be used */
            int weatherId = todayWeatherCursor.getInt(NotificationUtils.INDEX_WEATHER_ID);
            double high = todayWeatherCursor.getDouble(NotificationUtils.INDEX_MAX_TEMP);
            double low = todayWeatherCursor.getDouble(NotificationUtils.INDEX_MIN_TEMP);
            
            Resources resources = context.getResources();
            int largeArtResourceId = SunshineWeatherUtils.getSmallArtResourceIdForWeatherCondition(weatherId);
            
            Bitmap icon = BitmapFactory.decodeResource(resources, largeArtResourceId);
            Asset asset = SunshineIOUtils.createAssetFromBitmap(icon);
            
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/sunshine");
            putDataMapRequest.getDataMap().putDouble("max", high);
            putDataMapRequest.getDataMap().putDouble("min", low);
            putDataMapRequest.getDataMap().putAsset("icon", asset);
            Log.d(TAG, "Sending " + high + " " + low + " " + largeArtResourceId);
            
            PutDataRequest request = putDataMapRequest.asPutDataRequest();
            putDataMapRequest.setUrgent();
            ResultCallback<DataApi.DataItemResult> resultCallback = new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                    if (dataItemResult.getStatus().isSuccess()) {
                        Log.d(TAG, "Enviou");
                    } else {
                        Log.d(TAG, "Deu banzo");
                    }
                }
            };
            Wearable.DataApi.putDataItem(mGoogleApiClient, request).setResultCallback(resultCallback);
            Log.d(TAG, "Sended!");
        }
        todayWeatherCursor.close();
    }
}