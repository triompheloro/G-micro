package com.example.projet.ml;

import android.graphics.ImageFormat;
import android.media.Image;
import java.nio.ByteBuffer;

public class ImageUtils {
    public static ByteBuffer imageToByteBuffer(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        ByteBuffer nv21Buffer = ByteBuffer.allocateDirect(ySize + uSize + vSize);

        // Add Y plane
        nv21Buffer.put(yBuffer);
        // Add U and V planes
        for (int i = 0; i < vSize; i++) {
            nv21Buffer.put(vBuffer.get(i));
            nv21Buffer.put(uBuffer.get(i));
        }

        nv21Buffer.flip();
        return nv21Buffer;
    }
}