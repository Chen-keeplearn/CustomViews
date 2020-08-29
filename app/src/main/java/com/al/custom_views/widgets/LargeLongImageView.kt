package com.al.custom_views.widgets

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Scroller
import java.io.InputStream

/**
 * 大图长图加载：
 * 分块加载,内存复用
 * 如:用1M的内存来加载图片的第一部分,滑动查看图片第二部分时,将1M内存释放掉,用来加载图片的第二部分
 * 图片分区域：Rect
 * 内存复用：BitmapFactory.Options
 * 拉伸、手势：GestureDetector
 * 滚动：Scroller
 */
class LargeLongImageView @JvmOverloads constructor(
    c: Context,
    attrs: AttributeSet? = null,
    defaultAttr: Int = 0
) : View(c, attrs, defaultAttr), GestureDetector.OnGestureListener,
    ScaleGestureDetector.OnScaleGestureListener, GestureDetector.OnDoubleTapListener {


    //显示的图片块区域
    private var mRect: Rect = Rect()

    //options,分块加载,内存复用
    private var mOptions: BitmapFactory.Options = BitmapFactory.Options()

    //手势,图片拉动
    private val mGestureDetector: GestureDetector = GestureDetector(c, this)

    //手势缩放
    private val mScaleGestureDetector: ScaleGestureDetector = ScaleGestureDetector(c, this)

    //滚动类
    private var mScroll: Scroller = Scroller(c)

    private var mImageHeight: Int = 0
    private var mImageWidth: Int = 0
    private lateinit var mRegionDecoder: BitmapRegionDecoder
    private var mViewWidth: Int = 0
    private var mViewHeight: Int = 0

    private var mBitmap: Bitmap? = null

    private var mScale: Float = 1F

    //当前缩放因子
    private var mCurrentScale: Float = 1F

    //canvas绘制缩放图片时的matrix
    private val mMatrix = Matrix()

    //放大倍数
    private var mMultiple = 3

    /**
     * 传入图片
     * 在不把整张大图加载到内存的情况下,怎么分块加载？->先获取图片宽高
     */
    fun setImage(inputStream: InputStream) {
        //获取图片宽高等信息,不加载进内存
        mOptions.inJustDecodeBounds = true

        BitmapFactory.decodeStream(inputStream, null, mOptions)
        mImageWidth = mOptions.outWidth
        mImageHeight = mOptions.outHeight
        //开启复用
        mOptions.inMutable = true
        mOptions.inPreferredConfig = Bitmap.Config.RGB_565

        mOptions.inJustDecodeBounds = false

        //创建区域解码器
        mRegionDecoder = BitmapRegionDecoder.newInstance(inputStream, false)

        //绘制,显示出来：onMeasure、onDraw.....
        requestLayout()
    }


    /**
     * 测量：View、图片到底需要显示多大
     * 将图片加载到view上
     * 测量view的大小
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        //view的宽高
        mViewWidth = measuredWidth
        mViewHeight = measuredHeight

        //确定图片的显示区域
        mRect.left = 0
        mRect.right = mViewWidth
        mRect.top = 0

        //等比例压缩,图片显示在View上面,图片宽度显示成view宽度,那图片高度就是等比例缩放得到
        //计算缩放因子
        mScale = mViewWidth / mImageWidth.toFloat()

        mCurrentScale = mScale

        //这样计算的mScale是可能会有问题的,有可能是0.0
        //mScale = (mViewWidth / mImageWidth).toFloat()
        log("mViewWidth=$mViewWidth--mViewHeight==$mViewHeight--mImageWidth==$mImageWidth--mImageHeight==$mImageHeight")
        //计算显示区域bottom
        mRect.bottom = mViewHeight
        //mRect.bottom = (mViewHeight / mCurrentScale).toInt()
    }

    /**
     * 绘制
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        //复用, inBitmap:相当于 mOptions.inMutable = true开启复用后,每次复用的bitmap都是在Options对象的inBitmap中
        mOptions.inBitmap = mBitmap
        //得到的显示的区域图片
        mBitmap = mRegionDecoder.decodeRegion(mRect, mOptions)
        mMatrix.setScale(mCurrentScale, mCurrentScale)
        //画布是最多只有屏幕宽高这样的大小,需要将图片进行缩放
        canvas.drawBitmap(mBitmap!!, mMatrix, null)
    }


    /**
     * 按下事件
     */
    override fun onDown(e: MotionEvent?): Boolean {
        //手指按下时,mScroll还在滚动没有停止,强行停止
        if (!mScroll.isFinished) {
            mScroll.forceFinished(true)
        }
        //按下时,可能还会继续滑动,滑动事件才开始,所以这里返回true,拦截了事件
        return true
    }

    /**
     * 滚动:即改变绘制区域
     * 上下滚动滑动,就是不停的改变加载、绘制的区域
     * 注意:滑动到顶部和底部的时候,就不能向上和向下继续滑动了
     */
    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent?,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        /*//x方向是不变的
        mRect.offset(0, distanceY.toInt())

        if (mRect.bottom > mImageHeight) {
            //滑动到底部
            mRect.bottom = mImageHeight
            mRect.top = mImageHeight - (mViewHeight / mCurrentScale).toInt()
        }
        if (mRect.top < 0) {
            //滑动到顶部
            mRect.top = 0
            mRect.bottom = (mViewHeight / mCurrentScale).toInt()
        }*/
        mRect.offset(distanceX.toInt(), distanceY.toInt())
        handleRectBorder()
        //重绘,每次滑动都需要进行重绘
        invalidate()
        return false
    }

    /**
     * 滑动惯性
     * velocityY取负数
     */
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        //对于x来说,都是0,目前只处理y方向上的滑动
        //maxY:最大滑动的y,并不是图片的高度,而是图片的高度减去屏幕的高度
        //startY是从top开始的,并不是从0开始
        /*mScroll.fling(
            0,
            mRect.top,
            0,
            -velocityY.toInt(),
            0,
            0,
            0,
            mImageHeight - (mViewHeight / mCurrentScale).toInt()
        )*/
        mScroll.fling(
            mRect.left,
            mRect.top,
            (-velocityX).toInt(),
            -velocityY.toInt(),
            0,
            mImageWidth - (mViewWidth / mCurrentScale).toInt(),
            0,
            mImageHeight - (mViewHeight / mCurrentScale).toInt()
        )
        return false
    }

    /**
     * 处理惯性结果
     * 惯性滚动也是不停的在改变加载区域
     */
    override fun computeScroll() {
        if (mScroll.isFinished) {
            return
        }
        if (mScroll.computeScrollOffset()) {
            //拿到rect的top和bottom,然后重绘
            mRect.top = mScroll.currY
            mRect.bottom = mRect.top + (mViewHeight / mCurrentScale).toInt()
            invalidate()
        }
    }

    override fun onShowPress(e: MotionEvent?) {

    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        return false
    }


    override fun onLongPress(e: MotionEvent?) {

    }

    /**
     * onTouch直接交给手势处理
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        mGestureDetector.onTouchEvent(event)
        mScaleGestureDetector.onTouchEvent(event)
        return true
    }


    /**
     * 实现缩放,该方法需要返回true,否则无法监测到手势缩放
     */
    override fun onScaleBegin(p0: ScaleGestureDetector?): Boolean {
        return true
    }

    override fun onScaleEnd(p0: ScaleGestureDetector?) {
    }

    /**
     * 缩放
     */
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        //处理手指缩放事件
        //获取与上次事件相比，得到的比例因子
        val scaleFactor = detector.scaleFactor
        //mCurrentScale += scaleFactor - 1
        mCurrentScale *= scaleFactor
        if (mCurrentScale > mScale * mMultiple) {
            mCurrentScale = mScale * mMultiple
        } else if (mCurrentScale <= mScale) {
            mCurrentScale = mScale
        }
        mRect.right = (mRect.left + (mViewWidth / mCurrentScale)).toInt()
        mRect.bottom = (mRect.top + (mViewHeight / mCurrentScale)).toInt()
        invalidate()
        return true
    }

    /**
     * GestureDetector的双击事件
     */
    override fun onDoubleTap(event: MotionEvent): Boolean {
        mCurrentScale = if (mCurrentScale > mScale) {
            mScale
        } else {
            mScale * mMultiple
        }
        mRect.right = (mRect.left + (mViewWidth / mCurrentScale)).toInt()
        mRect.bottom = (mRect.top + (mViewHeight / mCurrentScale)).toInt()
        handleRectBorder()
        invalidate()
        return true
    }

    /**
     * 处理图片滑动到Rect区域的边界的问题
     */
    private fun handleRectBorder() {
        if (mRect.left < 0) {
            //左边
            mRect.left = 0
            mRect.right = (mViewWidth / mCurrentScale).toInt()
        }

        if (mRect.right > mImageWidth) {
            //右边
            mRect.right = mImageWidth
            mRect.left = mImageWidth - (mViewWidth / mCurrentScale).toInt()
        }

        if (mRect.top < 0) {
            //滑动到顶部
            mRect.top = 0
            mRect.bottom = (mViewHeight / mCurrentScale).toInt()
        }

        if (mRect.bottom > mImageHeight) {
            //滑动到底部
            mRect.bottom = mImageHeight
            mRect.top = mImageHeight - (mViewHeight / mCurrentScale).toInt()
        }
    }

    override fun onDoubleTapEvent(p0: MotionEvent?): Boolean {
        return false
    }

    override fun onSingleTapConfirmed(p0: MotionEvent?): Boolean {
        return false
    }

}

fun LargeLongImageView.log(msg: String) {
    Log.i("yl---", msg)
}