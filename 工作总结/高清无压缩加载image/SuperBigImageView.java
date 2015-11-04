package bollin.com;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.bollin1206.superbigimageload.MoveGestureDetector;

import java.io.IOException;
import java.io.InputStream;

/**
 * TODO: document your custom view class.
 */
public class SuperBigImageView extends View {

    private BitmapRegionDecoder bitmapRegionDecoder;
    //图片的宽高
    private  int imageWidth,imageHeight;
    //绘制的区域
    private  volatile Rect rect   =new Rect();
    //自定义手势
    private MoveGestureDetector moveGestureDetector;

    private  static final BitmapFactory.Options options = new BitmapFactory.Options();
    static {
        options.inPreferredConfig= Bitmap.Config.RGB_565;
    }

    public SuperBigImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        moveGestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Bitmap bm  = bitmapRegionDecoder.decodeRegion(rect,options);
        canvas.drawBitmap(bm, 0, 0, null);
        bm.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        rect.left = imageWidth/2-width/2;
        rect.top = imageHeight/2-height/2;
        rect.right = rect.left+width;
        rect.bottom =rect.top+height;
    }

    /**
     * 加载大图
     * @param inputStream
     */
    public  void setInputStream(InputStream inputStream){
        try {
            bitmapRegionDecoder = BitmapRegionDecoder.newInstance(inputStream,false);
            BitmapFactory.Options tmpOptions  = new BitmapFactory.Options();
            tmpOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream,null,tmpOptions);
            imageHeight = tmpOptions.outHeight;
            imageWidth = tmpOptions.outWidth;
//            requestLayout();
//            invalidate();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (inputStream!=null){
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public  void init(){
        moveGestureDetector = new MoveGestureDetector(getContext(),new MoveGestureDetector.SimpleMoveGestureDetector(){
            @Override
            public boolean onMove(MoveGestureDetector moveGestureDetector) {
                int moveX =(int) moveGestureDetector.getMoveX();
                int moveY = (int) moveGestureDetector.getMoveY();
                if (imageWidth>getWidth()){
                    rect.offset(-moveX,0);
                    checkWidth();
                    invalidate();
                }
                if (imageHeight>getHeight()){
                    rect.offset(0,-moveY);
                    checkHeight();
                    invalidate();
                }
                return  true;
            }

            @Override
            public boolean onMoveEnd(MoveGestureDetector moveGestureDetector) {
                return super.onMoveEnd(moveGestureDetector);
            }
        });
    }

    private void checkHeight() {
        Rect tmprect = rect;
        if (tmprect.bottom>imageHeight){
            tmprect.bottom = imageHeight;
            tmprect.top = imageHeight-getHeight();
        }
        if (tmprect.top<0){
            tmprect.top =0 ;
            tmprect.bottom =getHeight();
        }
    }

    private void checkWidth() {
        Rect tmprect = rect;
        if (tmprect.right>imageWidth){
            tmprect.right = imageWidth;
            tmprect.left = imageWidth-getWidth();
        }
        if (tmprect.left<0){
            tmprect.left =0 ;
            tmprect.right =getWidth();
        }
    }


}
