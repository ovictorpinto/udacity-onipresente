package com.example.android.sunshine.utilities;

import android.graphics.Bitmap;

import com.google.android.gms.wearable.Asset;

import java.io.ByteArrayOutputStream;

/**
 * Created by victorpinto on 29/04/17. 
 */

public class SunshineIOUtils {
    
    public static Asset createAssetFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }
}
