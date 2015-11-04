package com.bollin1206.superbigimageload;


import android.content.Context;
import android.graphics.PointF;
import android.view.MotionEvent;

/**
 * Created by wujun on 15/11/3.
 */
public class MoveGestureDetector extends  BaseGestureDetector{
    private PointF mCurrentPointer;
    private PointF mPrePointer;
    private  PointF mDeltaPointer  = new PointF();

    private PointF mExtenalPointer  = new PointF();//记录最终结果,返回
    private  OnMoveGestureListener moveGestureListener;
    public MoveGestureDetector(Context context,OnMoveGestureListener moveGestureListener) {
        super(context);
        this.moveGestureListener  = moveGestureListener;

    }

    @Override
    protected void handleInProgressEvent(MotionEvent event) {
        int actionCode  =event.getAction()&MotionEvent.ACTION_MASK;
        switch (actionCode){
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                moveGestureListener.onMoveEnd(this);
                resetState();
                break;
            case MotionEvent.ACTION_MOVE:
                updateStateByEvent(event);
                boolean update = moveGestureListener.onMove(this);
                if (update){
                    mPreMotionEvent.recycle();
                    mPreMotionEvent= MotionEvent.obtain(event);
                }
                break;
        }
    }

    @Override
    protected void handleStartProgressEvent(MotionEvent event) {
        int actionCode = event.getAction()&MotionEvent.ACTION_MASK;
        switch (actionCode){
            case MotionEvent.ACTION_DOWN:
                resetState();
                mPreMotionEvent =MotionEvent.obtain(event);
                updateStateByEvent(event);
                break;
            case MotionEvent.ACTION_MOVE:
                mGestureInProgress = moveGestureListener.onMoveStart(this);
                break;
        }
    }

    @Override
    protected void updateStateByEvent(MotionEvent event) {
        final  MotionEvent prev  = mPreMotionEvent;
        mPrePointer  =caculateFocalPointer(prev);
        mCurrentPointer =caculateFocalPointer(event);
        boolean mSkipMoveEvent = prev.getPointerCount() != event.getPointerCount();
        mExtenalPointer.x = mSkipMoveEvent?0:mCurrentPointer.x-mPrePointer.x;
        mExtenalPointer.y = mSkipMoveEvent?0:mCurrentPointer.y-mPrePointer.y;

    }

    /**
     * 根据event 计算中心点
     * @param event
     * @return
     */
    private PointF caculateFocalPointer(MotionEvent event) {
        final  int count  =event.getPointerCount();
        float x= 0 ,y = 0;
        for (int i = 0; i < count; i++) {
            x +=event.getX(i);
            y +=event.getY(i);
        }
            x /= count;
            y /= count;
        return new PointF(x,y);
    }

    public  float getMoveX(){
        return mExtenalPointer.x;
    }
    public  float getMoveY(){
        return  mExtenalPointer.y;
    }
    public static class SimpleMoveGestureDetector implements  OnMoveGestureListener {
        @Override
        public boolean onMoveStart(MoveGestureDetector moveGestureDetector) {
            return true;
        }

        @Override
        public boolean onMoveEnd(MoveGestureDetector moveGestureDetector) {
            return false;
        }

        @Override
        public boolean onMove(MoveGestureDetector moveGestureDetector) {
            return false;
        }
    }

    public  interface  OnMoveGestureListener{
        boolean onMoveStart(MoveGestureDetector moveGestureDetector);
        boolean onMoveEnd(MoveGestureDetector moveGestureDetector);
        boolean onMove(MoveGestureDetector moveGestureDetector);
    }
}
