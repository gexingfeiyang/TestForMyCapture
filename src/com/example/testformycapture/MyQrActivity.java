package com.example.testformycapture;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

public class MyQrActivity extends Activity
{
    protected int mScreenWidth;
    private ImageView imageView;
    private Bitmap logo;
    private static final int IMAGE_HALFWIDTH = 40;
    private Button btn_back;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_qr);
        imageView = (ImageView) findViewById(R.id.iv_qr_image);
        createImg();
        btn_back = (Button) findViewById(R.id.btn_back);
        btn_back.setOnClickListener(new OnClickListener()
        {
            
            @Override
            public void onClick(View v)
            {
                MyQrActivity.this.finish();
            }
        });
    }

    public void createImg()
    {
        String codeInfo = "";
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        Date curDate = new Date(System.currentTimeMillis());// 获取当前时间
        String str = formatter.format(curDate);
        codeInfo = str + "章文兵";
        logo = BitmapFactory.decodeResource(super.getResources(), R.drawable.icon_com);
        try
        {
            if (!codeInfo.equals(""))
            {
                // 根据字符串生成二维码图片并显示在界面上，第二个参数为图片的大小（350*350）
                Bitmap qrCodeBitmap = createCode(codeInfo, logo, BarcodeFormat.QR_CODE);
                imageView.setImageBitmap(qrCodeBitmap);
            } else
            {
                Toast.makeText(MyQrActivity.this, "Text can not be empty", Toast.LENGTH_SHORT).show();
            }
        } catch (WriterException e)
        {
            e.printStackTrace();
        }

    }

    /**
     * * 生成二维码
     * 
     * @param string
     *            二维码中包含的文本信息
     * @param mBitmap
     *            logo图片
     * @param format
     *            编码格式
     * @return Bitmap 位图
     * @throws WriterException
     */
    public Bitmap createCode(String string, Bitmap mBitmap, BarcodeFormat format) throws WriterException
    {
        Matrix m = new Matrix();
        float sx = (float) 2 * IMAGE_HALFWIDTH / mBitmap.getWidth();
        float sy = (float) 2 * IMAGE_HALFWIDTH / mBitmap.getHeight();
        m.setScale(sx, sy);// 设置缩放信息
        // 将logo图片按martix设置的信息缩放
        mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), m, false);
        MultiFormatWriter writer = new MultiFormatWriter();
        Hashtable hst = new Hashtable();
        hst.put(EncodeHintType.CHARACTER_SET, "UTF-8");// 设置字符编码
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screenWidth = dm.widthPixels;// 宽度
        int screenHeight = dm.heightPixels;// 高度
        BitMatrix matrix = writer.encode(string, format, screenWidth / 5 * 3, screenHeight / 5 * 2, hst);// 生成二维码矩阵信息
        int width = matrix.getWidth();// 矩阵高度
        int height = matrix.getHeight();// 矩阵宽度
        int halfW = width / 2;
        int halfH = height / 2;
        int[] pixels = new int[width * height];// 定义数组长度为矩阵高度*矩阵宽度，用于记录矩阵中像素信息
        for (int y = 0; y < height; y++)
        {
            // 从行开始迭代矩阵
            for (int x = 0; x < width; x++)
            {
                // 迭代列
                if (x > halfW - IMAGE_HALFWIDTH && x < halfW + IMAGE_HALFWIDTH && y > halfH - IMAGE_HALFWIDTH && y < halfH + IMAGE_HALFWIDTH)
                {
                    // 该位置用于存放图片信息
                    // 记录图片每个像素信息
                    pixels[y * width + x] = mBitmap.getPixel(x - halfW + IMAGE_HALFWIDTH, y - halfH + IMAGE_HALFWIDTH);
                } else
                {
                    if (matrix.get(x, y))
                    {
                        // 如果有黑块点，记录信息
                        pixels[y * width + x] = 0xff000000;// 记录黑块信息
                    }
                }
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        // 通过像素数组生成bitmap
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }
}
