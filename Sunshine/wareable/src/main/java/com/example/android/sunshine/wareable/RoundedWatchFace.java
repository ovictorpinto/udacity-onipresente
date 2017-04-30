/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.wareable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;

import com.example.android.sunshine.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 *
 * https://developer.android.com/training/wearables/apps/creating.html?hl=pt-br
 * /Users/victorpinto/Java/sdk/platform-tools/adb -d forward tcp:5601 tcp:5601
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 */
public class RoundedWatchFace extends CanvasWatchFaceService {
    
    /*
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    
    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private Double min;
    
    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }
    
    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {
        private static final float HOUR_STROKE_WIDTH = 4f;
        private static final int HOUR_SIZE = 55;
        private static final int DATE_SIZE = 23;
        private static final int MAX_SIZE = 32;
        private static final int LINE_SIZE = 60;
        private static final String TAG = "RoundedWatchFace";
        
        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(RoundedWatchFace.this).addConnectionCallbacks(this)
                                                                                             .addOnConnectionFailedListener(this)
                                                                                             .addApi(Wearable.API).build();
        
        private final Rect mPeekCardBounds = new Rect();
        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;
        
        private Paint mHourPaint;
        private Paint mDatePaint;
        private Paint mIconPaint;
        private Paint mMaxPaint;
        private Paint mMinPaint;
        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private SimpleDateFormat hourFormat;
        private SimpleDateFormat dateFormat;
        private Double max;
        private Bitmap iconBitmap;
        
        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            
            WatchFaceStyle.Builder builder = new WatchFaceStyle.Builder(RoundedWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE).setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true);
            setWatchFaceStyle(builder.build());
            
            mIconPaint = new Paint();
            
            mHourPaint = new Paint();
            mHourPaint.setColor(Color.WHITE);
            mHourPaint.setTextSize(HOUR_SIZE);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            
            mDatePaint = new Paint();
            mDatePaint.setTextSize(DATE_SIZE);
            mDatePaint.setColor(getColor(R.color.light_font));
            mDatePaint.setAntiAlias(true);
            
            mMaxPaint = new Paint();
            mMaxPaint.setTextSize(MAX_SIZE);
            mMaxPaint.setColor(Color.WHITE);
            mMaxPaint.setAntiAlias(true);
            
            mMinPaint = new Paint();
            mMinPaint.setTextSize(MAX_SIZE);
            mMinPaint.setColor(getColor(R.color.light_font));
            mMinPaint.setAntiAlias(true);
            
            mCalendar = Calendar.getInstance();
            
            hourFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            dateFormat = new SimpleDateFormat("EEE, MMM dd yyyy", Locale.getDefault());
            
            mGoogleApiClient.connect();
            
        }
        
        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }
        
        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }
        
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }
        
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;
            
            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }
        
        private void updateWatchHandStyle() {
            if (mAmbient) {
                mHourPaint.setAntiAlias(false);
                mDatePaint.setAntiAlias(false);
                mMinPaint.setColor(Color.WHITE);
                mDatePaint.setColor(Color.WHITE);
            } else {
                mMinPaint.setColor(getColor(R.color.light_font));
                mDatePaint.setColor(getColor(R.color.light_font));
                mHourPaint.setAntiAlias(true);
                mDatePaint.setAntiAlias(true);
            }
        }
        
        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (mAmbient) {
                canvas.drawColor(getColor(R.color.watch_background_inative));
            } else {
                canvas.drawColor(getColor(R.color.watch_background));
            }
            
            int centerY = bounds.height() / 2;
            float centerX = bounds.width() / 2;
            
            float yAtual = centerY - 40;
            Rect hourRect = new Rect();
            String hourText = hourFormat.format(mCalendar.getTime());
            mHourPaint.getTextBounds(hourText, 0, hourText.length(), hourRect);
            canvas.drawText(hourText, centerX - hourRect.centerX(), yAtual, mHourPaint);
            
            Rect dateRect = new Rect();
            String dateText = dateFormat.format(mCalendar.getTime()).toUpperCase(Locale.getDefault());
            mDatePaint.getTextBounds(dateText, 0, dateText.length(), dateRect);
            yAtual += hourRect.height() - 5;
            canvas.drawText(dateText, centerX - dateRect.centerX(), yAtual, mDatePaint);
            
            yAtual += dateRect.height() + 10;
            
            canvas.drawLine(centerX - LINE_SIZE / 2, yAtual, centerX + LINE_SIZE / 2, yAtual, mDatePaint);
            
            if (max != null) {
                Rect maxRect = new Rect();
                String maxText = String.format(Locale.getDefault(), "%.0fº", max);
                mMaxPaint.getTextBounds(maxText, 0, maxText.length(), maxRect);
                yAtual += maxRect.height() + 26;
                canvas.drawText(maxText, centerX - maxRect.centerX(), yAtual, mMaxPaint);
                
                String minText = String.format(Locale.getDefault(), "%.0fº", min);
                canvas.drawText(minText, centerX + maxRect.centerX() + 16, yAtual, mMinPaint);
                
                if (!isInAmbientMode() && iconBitmap != null) {
                    float xInit = centerX - maxRect.centerX() - iconBitmap.getWidth() - 16;
                    float yInit = yAtual - iconBitmap.getHeight() / 2 - 10;
                    canvas.drawBitmap(iconBitmap, xInit, yInit, mIconPaint);
                }
            }
            
        }
        
        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            
            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }
        
        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
            super.onPeekCardPositionUpdate(rect);
            mPeekCardBounds.set(rect);
        }
        
        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            RoundedWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }
        
        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            RoundedWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }
        
        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }
        
        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }
        
        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
        
        @Override
        public void onConnected(@Nullable Bundle connectionHint) {
            Log.d(TAG, "onConnected: " + connectionHint);
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }
        
        @Override
        public void onConnectionSuspended(int i) {
            
        }
        
        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            
        }
        
        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d(TAG, "Received");
            for (DataEvent dataEvent : dataEventBuffer) {
                DataItem item = dataEvent.getDataItem();
                if (item.getUri().getPath().compareTo("/sunshine") == 0) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(item);
                    DataMap config = dataMapItem.getDataMap();
                    max = config.getDouble("max");
                    min = config.getDouble("min");
                    final Asset iconAsset = config.getAsset("icon");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (iconBitmap != null) {
                                iconBitmap.recycle();
                                iconBitmap = null;
                            }
                            iconBitmap = loadBitmapFromAsset(iconAsset);
                            iconBitmap = Bitmap.createScaledBitmap(iconBitmap, 60, 60, true);
                        }
                    }).start();
                }
            }
        }
        
        public Bitmap loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset).await().getInputStream();
            
            if (assetInputStream == null) {
                Log.w(TAG, "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }
    }
    
    private static class EngineHandler extends Handler {
        private final WeakReference<RoundedWatchFace.Engine> mWeakReference;
        
        public EngineHandler(RoundedWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }
        
        @Override
        public void handleMessage(Message msg) {
            RoundedWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
