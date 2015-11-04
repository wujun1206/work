package com.bollin1206.superbigimageload;

import android.content.Context;
import android.view.MotionEvent;

/**
 * Created by wujun on 15/11/3.
 */
public abstract class BaseGestureDetector {
        protected  boolean mGestureInProgress;
        protected MotionEvent  mPreMotionEvent;
        protected MotionEvent mCurrentMotionEvent;

    protected Context context;

    public BaseGestureDetector(Context context) {
        this.context = context;
    }
    public  boolean onTouchEvent(MotionEvent event){
        if (!mGestureInProgress){
            handleStartProgressEvent(event);
        }else {
            handleInProgressEvent(event);
        }
        return  true;
    }


    protected  void  resetState(){
        if (mPreMotionEvent!=null){
            mPreMotionEvent.recycle();
            mPreMotionEvent =null;
        }
        if (mCurrentMotionEvent!=null){
            mCurrentMotionEvent.recycle();
            mCurrentMotionEvent=null;
        }
        mGestureInProgress =false;
    }
    protected abstract void handleInProgressEvent(MotionEvent event);

    protected abstract void handleStartProgressEvent(MotionEvent event);
    protected  abstract void  updateStateByEvent(MotionEvent event);

}
