package com.bollin1206.superbigimage.view;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamScene extends Scene {
    private static final String TAG = InputStreamScene.class.getSimpleName();

    private static final boolean DEBUG = false;
    private static final BitmapFactory.Options inputStreamSceneoptions = new BitmapFactory.Options();

    /**
     * What is the downsample size for the sample image?  1=1/2, 2=1/4 3=1/8, etc
     */
    private static final int DOWN_SAMPLE_SHIFT = 3;

    /**
     * How many bytes does one pixel use?
     */
    private final int BYTES_PER_PIXEL = 4;

    /**
     * What percent of total memory should we use for the cache? The bigger the cache,
     * the longer it takes to read -- 1.2 secs for 25%, 600ms for 10%, 500ms for 5%.
     * User experience seems to be best for smaller values.
     */
    private int percent = 5; // Above 25 and we get OOMs

    private BitmapRegionDecoder regionDecoder;
    private Bitmap sampleBitmap;

    static {
        inputStreamSceneoptions.inPreferredConfig = Bitmap.Config.RGB_565;
    }

    public InputStreamScene(InputStream inputStream) throws IOException {
        BitmapFactory.Options tmpOptions = new BitmapFactory.Options();

        this.regionDecoder = BitmapRegionDecoder.newInstance(inputStream, false);

        // Grab the bounds for the scene dimensions
        tmpOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, tmpOptions);
        setSceneSize(tmpOptions.outWidth, tmpOptions.outHeight);//设置scene尺寸为整个原始加载图片的尺寸

        // Create the sample image
        tmpOptions.inJustDecodeBounds = false;
        tmpOptions.inSampleSize = (1 << DOWN_SAMPLE_SHIFT);
        sampleBitmap = BitmapFactory.decodeStream(inputStream, null, tmpOptions);

        initialize();
    }

    @Override
    protected Bitmap fillCache(Rect origin) {
        Bitmap bitmap = null;
        if (regionDecoder != null)
            bitmap = regionDecoder.decodeRegion(origin, inputStreamSceneoptions);
        return bitmap;
    }

    @Override
    protected void drawSampleRectIntoBitmap(Bitmap currentViewportBitmap, Rect windowViewportRect) {
        if (currentViewportBitmap != null) {
            Canvas canvas = new Canvas(currentViewportBitmap);
            int left = (windowViewportRect.left >> DOWN_SAMPLE_SHIFT);
            int top = (windowViewportRect.top >> DOWN_SAMPLE_SHIFT);
            int right = left + (windowViewportRect.width() >> DOWN_SAMPLE_SHIFT);
            int bottom = top + (windowViewportRect.height() >> DOWN_SAMPLE_SHIFT);
            Rect srcRect = new Rect(left, top, right, bottom);
            Rect identityRect = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
            canvas.drawBitmap(
                    sampleBitmap,
                    srcRect,
                    identityRect,
                    null
            );
//            canvas.drawLine(0L, 0L, canvas.getWidth(), canvas.getHeight(),redPaint);
        }
    }

//    @Override
//    protected Rect calculateCacheWindow(Rect viewportRect) {
//        // Simplest implementation
//        return viewportRect;
//    }
    private static Paint redPaint = new Paint();
    static{
        redPaint.setColor(Color.RED);
        redPaint.setStrokeWidth(5L);
    }
    private Rect calculatedCacheWindowRect = new Rect();

    @Override
    protected Rect calculateCacheWindow(Rect viewportRect) {
        long bytesToUse = Runtime.getRuntime().maxMemory() * percent / 100;
        Point sceneSizePoint = getSceneSize();

        int viewportRectWidth = viewportRect.width();
        int viewportRectHeight = viewportRect.height();

        // Calculate the max size of the margins to fit in our memory budget
        int tw = 0;
        int th = 0;
        int mw = tw;
        int mh = th;
        while ((viewportRectWidth + tw) * (viewportRectHeight + th) * BYTES_PER_PIXEL < bytesToUse) {
            mw = tw++;
            mh = th++;
        }

        // Trim the margins if they're too big.
        if (viewportRectWidth + mw > sceneSizePoint.x) // viewport width + margin width > width of the image
            mw = Math.max(0, sceneSizePoint.x - viewportRectWidth);
        if (viewportRectHeight + mh > sceneSizePoint.y) // viewport height + margin height > height of the image
            mh = Math.max(0, sceneSizePoint.y - viewportRectHeight);

        // Figure out the left & right based on the margin. We assume our viewportRect
        // is <= our size. If that's not the case, then this logic breaks.
        int left = viewportRect.left - (mw >> 1);
        int right = viewportRect.right + (mw >> 1);
        if (left < 0) {
            right = right - left; // Add's the overage on the left side back to the right
            left = 0;
        }
        if (right > sceneSizePoint.x) {
            left = left - (right - sceneSizePoint.x); // Adds overage on right side back to left
            right = sceneSizePoint.x;
        }

        // Figure out the top & bottom based on the margin. We assume our viewportRect
        // is <= our size. If that's not the case, then this logic breaks.
        int top = viewportRect.top - (mh >> 1);
        int bottom = viewportRect.bottom + (mh >> 1);
        if (top < 0) {
            bottom = bottom - top; // Add's the overage on the top back to the bottom
            top = 0;
        }
        if (bottom > sceneSizePoint.y) {
            top = top - (bottom - sceneSizePoint.y); // Adds overage on bottom back to top
            bottom = sceneSizePoint.y;
        }

        // Set the origin based on our new calculated values.
        calculatedCacheWindowRect.set(left, top, right, bottom);
        if (DEBUG)
            Log.d(TAG, "new cache.originRect = " + calculatedCacheWindowRect.toShortString() + " size=" + sceneSizePoint.toString());
        return calculatedCacheWindowRect;
    }

    @Override
    protected void fillCacheOutOfMemoryError(OutOfMemoryError error) {
        if (percent > 0)
            percent -= 1;
        Log.e(TAG, String.format("caught oom -- cache now at %d percent.", percent));
    }

    @Override
    protected void drawComplete(Canvas canvas) {
        // TODO Auto-generated method stub

    }
}
