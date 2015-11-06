package com.bollin1206.superbigimage.view;

import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.os.Debug;
import android.util.Log;

/*
 * +-------------------------------------------------------------------+
 * |                                        |                          |
 * |  +------------------------+            |                          |
 * |  |                        |            |                          |
 * |  |                        |            |                          |
 * |  |                        |            |                          |
 * |  |        显示窗口         |            |                          |
 * |  +------------------------+            |                          |
 * |                                        |                          |
 * |                                        |                          |
 * |                                        |                          |
 * |                        缓存尺寸         |                          |
 * |----------------------------------------+                          |
 * |                                                                   |
 * |                                                                   |
 * |                                                                   |
 * |                                                                   |
 * |                                                                   |
 * |                               实际超大图片尺寸                      |
 * +-------------------------------------------------------------------+
 */

/**
 * 有这样一个场景: 加载一个巨大位图（或虚拟位图)到内存中.继承这个类并扩展它的抽象方法
 * 就能返回所需的显示位图。
 */
public abstract class Scene {
    private final String TAG = "Scene";

    private final static int MINIMUM_PIXELS_IN_VIEW = 50;

    /**
     * The size of the Scene
     */
    private Point scenePoint = new Point();
    /**
     * The viewport
     */
    private final Viewport viewport = new Viewport();
    /**
     * The cache
     */
    private final Cache cache = new Cache();

    //region [gs]etSceneSize

    /**
     * 设置窗口的尺寸
     * Set the size of the scene
     */
    public void setSceneSize(int width, int height) {
        scenePoint.set(width, height);
    }

    /**
     * 返窗口尺寸坐标
     * Returns a Point representing the size of the scene. Don't modify the returned Point!
     */
    public Point getSceneSize() {

        return scenePoint;
    }

    /**
     * 设置传入的坐标
     * Set the passed-in point to the size of the scene
     */
    public void getSceneSize(Point point) {
        point.set(scenePoint.x, scenePoint.y);
    }
    //endregion

    //region getViewport()
    public Viewport getViewport() {
        return viewport;
    }
    //endregion

    //region initialize/start/stop/suspend/invalidate the cache

    /**
     * Initializes the cache
     */
    public void initialize() {
        if (cache.getCacheState() == CacheState.UNINITIALIZED) {
            synchronized (cache) {
                cache.setCacheState(CacheState.INITIALIZED);
            }
        }
    }

    /**
     * Starts the cache thread
     */
    public void start() {
        cache.start();
    }

    /**
     * Stops the cache thread
     */
    public void stop() {
        cache.stop();
    }

    /**
     * Suspends or unsuspends the cache thread. This can be
     * used to temporarily stop the cache from updating
     * during a fling event.
     *
     * @param suspend True to suspend the cache. False to unsuspend.
     */
    public void setSuspend(boolean suspend) {
        if (suspend) {
            synchronized (cache) {
                cache.setCacheState(CacheState.SUSPEND);
            }
        } else {
            if (cache.getCacheState() == CacheState.SUSPEND) {
                synchronized (cache) {
                    cache.setCacheState(CacheState.INITIALIZED);
                }
            }
        }
    }

    /**
     * Invalidate the cache. This causes it to refill
     */
    @SuppressWarnings("unused")
    public void invalidate() {
        cache.invalidate();
    }
    //endregion

    //region void draw(Canvas c)

    /**
     * Draw the scene to the canvas. This operation fills the canvas with
     * the bitmap referenced by the viewport's location within the Scene.
     * If the cache already has the data (and is not suspended), then the
     * high resolution bitmap from the cache is used. If it's not available,
     * then the lower resolution bitmap from the sample is used.
     */
    public void draw(Canvas c) {
        viewport.draw(c);
    }
    //endregion

    //region protected abstract

    /**
     * This method must return a high resolution Bitmap that the Scene
     * will use to fill out the viewport bitmap upon request. This bitmap
     * is normally larger than the viewport so that the viewport can be
     * scrolled without having to refresh the cache. This method runs
     * on a thread other than the UI thread, and it is not under a lock, so
     * it is expected that this method can run for a long time (seconds?).
     *
     * @param rectOfCache The Rect representing the area of the Scene that
     *                    the Scene wants cached.
     * @return the Bitmap representing the requested area of the larger bitmap
     */
    protected abstract Bitmap fillCache(Rect rectOfCache);

    /**
     * The memory allocation you just did in fillCache caused an OutOfMemoryError.
     * You can attempt to recover. Experience shows that when we get an
     * OutOfMemoryError, we're pretty hosed and are going down. For instance, if
     * we're trying to decode a bitmap region with
     * {@link android.graphics.BitmapRegionDecoder} and we run out of memory,
     * we're going to die somewhere in the C code with a SIGSEGV.
     *
     * @param error The OutOfMemoryError exception data
     */
    protected abstract void fillCacheOutOfMemoryError(OutOfMemoryError error);

    /**
     * Calculate the Rect of the cache's window based on the current viewportRect.
     * The returned Rect must at least contain the viewportRect, but it can be
     * larger if the system believes a bitmap of the returned size will fit into
     * memory. This function must be fast as it happens while the cache lock is held.
     *
     * @param viewportRect The returned must be able to contain this Rect
     * @return The Rect that will be used to fill the cache
     */
    protected abstract Rect calculateCacheWindow(Rect viewportRect);

    /**
     * 该方法用于填充传入的样本数据
     * 用户体验质量依赖于该功能的速度
     * This method fills the passed-in bitmap with sample data. This function must
     * return as fast as possible so it shouldn't have to do any IO at all -- the
     * quality of the user experience rests on the speed of this function.
     *
     * @param currentViewportBitmap       The Bitmap to fill
     * @param rectOfSample Rectangle within the Scene that this bitmap represents.
     */
    protected abstract void drawSampleRectIntoBitmap(Bitmap currentViewportBitmap, Rect rectOfSample);

    /**
     * The Cache is done drawing the bitmap -- time to add the finishing touches
     *
     * @param canvas a canvas on which to draw
     */
    protected abstract void drawComplete(Canvas canvas);
    //endregion

    //region class Viewport

    /**
     * 视图
     */
    public class Viewport {
        /**
         * The bitmap of the current viewport
         */
        Bitmap currentViewportBitmap = null;
        /**
         * 一个用于在场景中定义图像的矩形
         * A Rect that defines where the Viewport is within the scene
         */
        final Rect windowViewportRect = new Rect(0, 0, 0, 0);
        float zoom = 1.0f;//数字越小图片越模糊,放大.反之缩小

        public void setOrigin(int x, int y) {
            synchronized (this) {
                int w = windowViewportRect.width();
                int h = windowViewportRect.height();

                // check bounds
                if (x < 0)
                    x = 0;

                if (y < 0)
                    y = 0;

                if (x + w > scenePoint.x)
                    x = scenePoint.x - w;

                if (y + h > scenePoint.y)
                    y = scenePoint.y - h;

                windowViewportRect.set(x, y, x + w, y + h);
            }
        }

        public void setViewportSize(int w, int h) {
            synchronized (this) {
                if (currentViewportBitmap != null) {
                    currentViewportBitmap.recycle();
                    currentViewportBitmap = null;
                }
                currentViewportBitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
                windowViewportRect.set(
                        windowViewportRect.left,
                        windowViewportRect.top,
                        windowViewportRect.left + w,
                        windowViewportRect.top + h);
            }
        }

        public void getOrigin(Point p) {
            synchronized (this) {
                p.set(windowViewportRect.left, windowViewportRect.top);
            }
        }

        public void getViewportSize(Point p) {
            synchronized (this) {
                p.x = windowViewportRect.width();
                p.y = windowViewportRect.height();
            }
        }

        public void getPhysicalSize(Point p) {
            synchronized (this) {
                p.x = getPhysicalWidth();
                p.y = getPhysicalHeight();
            }
        }

        public int getPhysicalWidth() {
            return currentViewportBitmap.getWidth();
        }

        public int getPhysicalHeight() {
            return currentViewportBitmap.getHeight();
        }

        public float getZoom() {
            return zoom;
        }

        /**
         * @param factor      缩放比例
         * @param screenFocus 触摸位置 相对于手机屏幕的坐标
         */
        public void zoom(float factor, PointF screenFocus) {
            if (factor != 1.0) {

                PointF screenSize = new PointF(currentViewportBitmap.getWidth(), currentViewportBitmap.getHeight());//屏幕的尺寸screenSize(x=2413,y=1440)
                PointF sceneSize = new PointF(getSceneSize());//原整体图片尺寸固定不变
                float screenWidthToHeight = screenSize.x / screenSize.y;//宽高比
                float screenHeightToWidth = screenSize.y / screenSize.x;//高宽比
                synchronized (this) {
                    float newZoom = zoom * factor;
                    RectF w1 = new RectF(windowViewportRect);//
                    RectF w2 = new RectF();
                    PointF sceneFocus = new PointF(//触控位置 相对在图片上位置的焦点坐标
                            w1.left + (screenFocus.x / screenSize.x) * w1.width(),
                            w1.top + (screenFocus.y / screenSize.y) * w1.height()
                    );
                    float w2Width = getPhysicalWidth() * newZoom;
                    if (w2Width > sceneSize.x) {
                        w2Width = sceneSize.x;
                        newZoom = w2Width / getPhysicalWidth();
                    }
                    if (w2Width < MINIMUM_PIXELS_IN_VIEW) {
                        w2Width = MINIMUM_PIXELS_IN_VIEW;
                        newZoom = w2Width / getPhysicalWidth();
                    }
                    float w2Height = w2Width * screenHeightToWidth;
                    if (w2Height > sceneSize.y) {
                        w2Height = sceneSize.y;
                        w2Width = w2Height * screenWidthToHeight;
                        newZoom = w2Width / getPhysicalWidth();
                    }
                    if (w2Height < MINIMUM_PIXELS_IN_VIEW) {
                        w2Height = MINIMUM_PIXELS_IN_VIEW;
                        w2Width = w2Height * screenWidthToHeight;
                        newZoom = w2Width / getPhysicalWidth();
                    }
                    w2.left = sceneFocus.x - ((screenFocus.x / screenSize.x) * w2Width);
                    w2.top = sceneFocus.y - ((screenFocus.y / screenSize.y) * w2Height);
                    if (w2.left < 0)
                        w2.left = 0;
                    if (w2.top < 0)
                        w2.top = 0;
                    w2.right = w2.left + w2Width;
                    w2.bottom = w2.top + w2Height;
                    if (w2.right > sceneSize.x) {
                        w2.right = sceneSize.x;
                        w2.left = w2.right - w2Width;
                    }
                    if (w2.bottom > sceneSize.y) {
                        w2.bottom = sceneSize.y;
                        w2.top = w2.bottom - w2Height;
                    }
                    windowViewportRect.set((int) w2.left, (int) w2.top, (int) w2.right, (int) w2.bottom);
                    zoom = newZoom;
                    Log.d(TAG, String.format(
                            "f=%.2f, z=%.2f, screenFocus触摸位置相对于手机屏幕对应的焦点坐标(%.0f,%.0f), sceneFocus触控位置 相对在图片上位置的焦点坐标(%.0f,%.0f) ,图片尺寸width,height(x=%.0f,y=%.0f), screenSize(x=%.0f,y=%.0f),w1s(%.0f,%.0f) w2s物理(%.0f,%.0f) w1原位置坐标(%.0f,%.0f,%.0f,%.0f) w2更新位置坐标(%.0f,%.0f,%.0f,%.0f)",
                            factor,
                            zoom,
                            screenFocus.x,
                            screenFocus.y,
                            sceneFocus.x,
                            sceneFocus.y,
                            sceneSize.x,
                            sceneSize.y,
                            screenSize.x,
                            screenSize.y,
                            w1.width(), w1.height(),
                            w2Width, w2Height,
                            w1.left, w1.top, w1.right, w1.bottom,
                            w2.left, w2.top, w2.right, w2.bottom
                    ));
                }
            }
        }

        void draw(Canvas canvas) {
            cache.update(this);
            synchronized (this) {
                if (canvas != null && currentViewportBitmap != null) {
                    canvas.drawBitmap(currentViewportBitmap, 0F, 0F, null);
                    drawComplete(canvas);
                }
            }
        }
    }
    //endregion

    //region class Cache

    private enum CacheState {UNINITIALIZED, INITIALIZED, START_UPDATE, IN_UPDATE, READY, SUSPEND}

    /**
     * 跟踪缓存的图片
     * Keep track of the cached bitmap
     */
    private class Cache {
        /**
         * 定义了场景内的缓存的矩形
         * A Rect that defines where the Cache is within the scene
         */
        final Rect windowCacheRect = new Rect(0, 0, 0, 0);
        /**
         * The bitmap of the current cache
         */
        Bitmap currentCacheBitmap = null;
        CacheState state = CacheState.UNINITIALIZED;

        void setCacheState(CacheState newState) {
            if (Debug.isDebuggerConnected())
                Log.i("bollin", String.format("cacheState old=%s new=%s", state.toString(), newState.toString()));
            state = newState;
        }

        CacheState getCacheState() {
            return state;
        }

        /**
         * Our load from disk thread
         */
        CacheThread cacheThread;

        void start() {
            if (cacheThread != null) {
                cacheThread.setRunning(false);
                cacheThread.interrupt();
                cacheThread = null;
            }
            cacheThread = new CacheThread(this);
            cacheThread.setName("cacheThread");
            cacheThread.start();
        }

        void stop() {
            cacheThread.running = false;
            cacheThread.interrupt();

            boolean retry = true;
            while (retry) {
                try {
                    cacheThread.join();
                    retry = false;
                } catch (InterruptedException e) {
                    // we will try it again and again...
                }
            }
            cacheThread = null;
        }

        void invalidate() {
            synchronized (this) {
                setCacheState(CacheState.INITIALIZED);
                cacheThread.interrupt();
            }
        }

        /**
         * Fill the bitmap with the part of the scene referenced by the viewport Rect
         */
        void update(Viewport viewport) {
            Bitmap bitmap = null;    // If this is null at the bottom, then load from the sample
            synchronized (this) {
                switch (getCacheState()) {
                    case UNINITIALIZED:
                        // nothing can be done -- should never get here
                        return;
                    case INITIALIZED:
                        // time to cache some data
                        setCacheState(CacheState.START_UPDATE);
                        cacheThread.interrupt();
                        break;
                    case START_UPDATE:
                        // I already told the thread to start
                        break;
                    case IN_UPDATE:
                        // Already reading some data, just use the sample
                        break;
                    case SUSPEND:
                        // Loading from cache suspended.
                        break;
                    case READY:
                        // I have some data to show
                        if (currentCacheBitmap == null) {
                            // Start the cache off right
                            if (Debug.isDebuggerConnected())
                                Log.d(TAG, "currentCacheBitmap is null");
                            setCacheState(CacheState.START_UPDATE);
                            cacheThread.interrupt();
                        } else if (!windowCacheRect.contains(viewport.windowViewportRect)) {
                            if (Debug.isDebuggerConnected())
                                Log.d(TAG, "viewport not in cache");
                            setCacheState(CacheState.START_UPDATE);
                            cacheThread.interrupt();
                        } else {
                            // Happy case -- the cache already contains the Viewport
                            bitmap = currentCacheBitmap;
                        }
                        break;
                }
            }
            if (bitmap == null)
                loadSampleIntoViewport();
            else
                loadBitmapIntoViewport(bitmap);
        }

        void loadBitmapIntoViewport(Bitmap bitmap) {
            if (bitmap != null) {
                synchronized (viewport) {
                    int left = viewport.windowViewportRect.left - windowCacheRect.left;
                    int top = viewport.windowViewportRect.top - windowCacheRect.top;
                    int right = left + viewport.windowViewportRect.width();
                    int bottom = top + viewport.windowViewportRect.height();
                    viewport.getPhysicalSize(dstSizePoint);
                    srcRect.set(left, top, right, bottom);
                    dstRect.set(0, 0, dstSizePoint.x, dstSizePoint.y);
                    Canvas canvas = new Canvas(viewport.currentViewportBitmap);
                    canvas.drawBitmap(
                            bitmap,
                            srcRect,
                            dstRect,
                            null);
//                    try {
//                        FileOutputStream fos = new FileOutputStream("/sdcard/viewport.png");
//                        viewport.bitmap.compress(Bitmap.CompressFormat.PNG, 99, fos);
//                        Thread.sleep(1000);
//                    } catch  (Exception e){
//                        System.out.print(e.getMessage());
//                    }
                }
            }
        }

        final Rect srcRect = new Rect(0, 0, 0, 0);
        final Rect dstRect = new Rect(0, 0, 0, 0);
        final Point dstSizePoint = new Point();

        void loadSampleIntoViewport() {
            if (getCacheState() != CacheState.UNINITIALIZED) {
                synchronized (viewport) {
                    drawSampleRectIntoBitmap(
                            viewport.currentViewportBitmap,
                            viewport.windowViewportRect
                    );
                }
            }
        }
    }
    //endregion

    //region class CacheThread

    /**
     * <p>The CacheThread's job is to wait until the {@link Cache#state} is
     * {@link CacheState#START_UPDATE} and then update the {@link Cache} given
     * the current {@link Viewport#windowViewportRect}. It does not want to hold the cache
     * lock during the call to {@link Scene#fillCache(Rect)} because the call
     * can take a long time. If we hold the lock, the user experience is very
     * jumpy.</p>
     * <p>The CacheThread and the {@link Cache} work hand in hand, both using the
     * cache itself to synchronize on and using the {@link Cache#state}.
     * The {@link Cache} is free to update any part of the cache object as long
     * as it holds the lock. The CacheThread is careful to make sure that it is
     * the {@link Cache#state} is {@link CacheState#IN_UPDATE} as it updates
     * the {@link Cache}. It locks and unlocks the cache all along the way, but
     * makes sure that the cache is not locked when it calls
     * {@link Scene#fillCache(Rect)}.
     */
    class CacheThread extends Thread {
        final Cache cache;
        boolean running = false;

        void setRunning(boolean value) {
            running = value;
        }

        CacheThread(Cache cache) {
            this.cache = cache;
        }

        @Override
        public void run() {
            running = true;
            Rect viewportRect = new Rect(0, 0, 0, 0);
            while (running) {
                while (running && cache.getCacheState() != CacheState.START_UPDATE)
                    try {
                        // Sleep until we have something to do
                        Thread.sleep(Integer.MAX_VALUE);
                    } catch (InterruptedException ignored) {
                    }
                if (!running)
                    return;
                long start = System.currentTimeMillis();
                boolean cont = false;
                synchronized (cache) {
                    if (cache.getCacheState() == CacheState.START_UPDATE) {
                        cache.setCacheState(CacheState.IN_UPDATE);
                        cache.currentCacheBitmap = null;
                        cont = true;
                    }
                }
                if (cont) {
                    synchronized (viewport) {
                        viewportRect.set(viewport.windowViewportRect);
                    }
                    synchronized (cache) {
                        if (cache.getCacheState() == CacheState.IN_UPDATE)
                            //cache.setWindowRect(viewportRect);
                            cache.windowCacheRect.set(calculateCacheWindow(viewportRect));
                        else
                            cont = false;
                    }
                    if (cont) {
                        try {
                            Bitmap bitmapCache = fillCache(cache.windowCacheRect);
                            if (bitmapCache != null) {
                                synchronized (cache) {
                                    if (cache.getCacheState() == CacheState.IN_UPDATE) {
                                        cache.currentCacheBitmap = bitmapCache;
                                        cache.setCacheState(CacheState.READY);
                                    } else {
                                        Log.w(TAG, "fillCache operation aborted");
                                    }
                                }
                            }
                            long done = System.currentTimeMillis();
                            if (Debug.isDebuggerConnected())
                                Log.d(TAG, String.format("fillCache in %dms", done - start));
                        } catch (OutOfMemoryError e) {
                            Log.d(TAG, "CacheThread out of memory");
                            /*
                             *  Attempt to recover. Experience shows that if we
                             *  do get an OutOfMemoryError, we're pretty hosed and are going down.
                             */
                            synchronized (cache) {
                                fillCacheOutOfMemoryError(e);
                                if (cache.getCacheState() == CacheState.IN_UPDATE) {
                                    cache.setCacheState(CacheState.START_UPDATE);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    //endregion
}
