package com.google.zxing.view;

import java.util.Collection;
import java.util.HashSet;

import com.example.testformycapture.R;
import com.google.zxing.ResultPoint;
import com.google.zxing.camera.CameraManager;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points.
 */
public final class ViewfinderView extends View {

    private static final int[] SCANNER_ALPHA = {
            0, 64, 128, 192, 255, 192, 128, 64
    };
    private static final long ANIMATION_DELAY = 100L;
    private static final int OPAQUE = 0xFF;
	private static final int SPEEN_DISTANCE = 10;
	private static final int MIDDLE_LINE_PADDING = 5;
	private static final int MIDDLE_LINE_WIDTH = 5;

    private final Paint paint;
    private Bitmap resultBitmap;
    private final int maskColor;
    private final int resultColor;
    private final int frameColor;
    private final int resultPointColor;
    private Collection<ResultPoint> possibleResultPoints;
    private Collection<ResultPoint> lastPossibleResultPoints;
    private boolean isFirst;
    private int slideTop;
	private int slideBottom;

	private Bitmap qrLineBitmap;
	private int qrWidth;
	private int qrHeight;
	private Rect qrSrc;
    private Rect qrDst;

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every
        // time in onDraw().
        paint = new Paint();
        Resources resources = getResources();
        
        qrLineBitmap = BitmapFactory.decodeResource(resources, R.drawable.qrcode_scan_light_green);
        qrWidth = qrLineBitmap.getWidth();
        qrHeight = qrLineBitmap.getHeight();
        qrSrc=new Rect(0, 0, qrWidth, qrHeight);

        maskColor = resources.getColor(R.color.viewfinder_mask);
        resultColor = resources.getColor(R.color.result_view);
        frameColor = resources.getColor(R.color.viewfinder_frame);
        resultPointColor = resources.getColor(R.color.possible_result_points);
        possibleResultPoints = new HashSet<ResultPoint>(5);
    }

    @Override
    public void onDraw(Canvas canvas) {
        Rect frame = CameraManager.get().getFramingRect();
        if (frame == null) {
            return;
        }
        
        if(!isFirst){  
            isFirst = true;  
            slideTop = frame.top;  
            slideBottom = frame.bottom;
        }  
        
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Draw the exterior (i.e. outside the framing rect) darkened
        paint.setColor(resultBitmap != null ? resultColor : maskColor);
		canvas.drawRect(0, 0, width, frame.top, paint);
		canvas.drawRect(0, frame.top, frame.left, frame.bottom, paint);
		canvas.drawRect(frame.right, frame.top, width, frame.bottom, paint);
		canvas.drawRect(0, frame.bottom, width, height, paint);
        
		Paint paint2 = new Paint();
		paint2.setColor(getResources().getColor(R.color.white));
		paint2.setStyle(Style.STROKE);
        canvas.drawRect(frame.left, frame.top, frame.right, frame.bottom, paint2);
        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(OPAQUE);
            canvas.drawBitmap(resultBitmap, frame.left, frame.top, paint);
        } else {
        	
            slideTop += SPEEN_DISTANCE;  
            if(slideTop >= frame.bottom - 30){  
                slideTop = frame.top;  
            }

            qrDst=new Rect(frame.left, slideTop, frame.right, slideTop+qrHeight);
            canvas.drawBitmap(qrLineBitmap,qrSrc,qrDst ,null);

            int linewidth = 10;
//            paint.setColor(frameColor);

            // draw rect
            paint.setColor(getResources().getColor(R.color.blue));
			canvas.drawRect(frame.left - 10, frame.top - 10, frame.left + 30,
					frame.top + 1, paint);
			canvas.drawRect(frame.left - 10, frame.top - 10, frame.left + 1,
					frame.top + 30, paint);
			canvas.drawRect(frame.right - 30, frame.top - 10, frame.right + 10,
					frame.top + 1, paint);
			canvas.drawRect(frame.right - 1, frame.top - 10, frame.right + 10,
					frame.top + 30, paint);
			canvas.drawRect(frame.left - 10, frame.bottom - 1, frame.left + 30,
					frame.bottom + 10, paint);
			canvas.drawRect(frame.left - 10, frame.bottom - 30, frame.left + 1,
					frame.bottom, paint);
			canvas.drawRect(frame.right - 30, frame.bottom - 1, frame.right + 9,
					frame.bottom + 10, paint);
			canvas.drawRect(frame.right - 1, frame.bottom - 30, frame.right + 9,
					frame.bottom + 10, paint);
			

            Collection<ResultPoint> currentPossible = possibleResultPoints;
            Collection<ResultPoint> currentLast = lastPossibleResultPoints;
            if (currentPossible.isEmpty()) {
                lastPossibleResultPoints = null;
            } else {
                possibleResultPoints = new HashSet<ResultPoint>(5);
                lastPossibleResultPoints = currentPossible;
                paint.setAlpha(OPAQUE);
                paint.setColor(resultPointColor);
                for (ResultPoint point : currentPossible) {
                    canvas.drawCircle(frame.left + point.getX(), frame.top
                            + point.getY(), 6.0f, paint);
                }
            }
            if (currentLast != null) {
                paint.setAlpha(OPAQUE / 2);
                paint.setColor(resultPointColor);
                for (ResultPoint point : currentLast) {
                    canvas.drawCircle(frame.left + point.getX(), frame.top
                            + point.getY(), 3.0f, paint);
                }
            }

            postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top,
                    frame.right, frame.bottom);
        }
    }

    public void drawViewfinder() {
        resultBitmap = null;
        invalidate();
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live
     * scanning display.
     * 
     * @param barcode An image of the decoded barcode.
     */
    public void drawResultBitmap(Bitmap barcode) {
        resultBitmap = barcode;
        invalidate();
    }

    public void addPossibleResultPoint(ResultPoint point) {
        possibleResultPoints.add(point);
    }

}
