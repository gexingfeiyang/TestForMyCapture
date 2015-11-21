package com.example.testformycapture;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.camera.CameraManager;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.decoding.CaptureActivityHandler;
import com.google.zxing.decoding.InactivityTimer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.view.ViewfinderView;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class CaptureActivity extends Activity implements Callback, OnClickListener
{

    private CaptureActivityHandler handler;
    private ViewfinderView viewfinderView;
    private boolean hasSurface;
    private Vector<BarcodeFormat> decodeFormats;
    private String characterSet;
    private InactivityTimer inactivityTimer;
    private MediaPlayer mediaPlayer;
    private boolean playBeep;
    private static final float BEEP_VOLUME = 0.10f;
    private boolean vibrate;
    private Button btn_back;
    private TextView prompt1;
    private TextView prompt2;
    private Button photo;
    private Button flash;
    private Button myqrcode;
    private boolean isOpen;
    private Parameters parameters;
    private static final int MIN_FRAME_HEIGHT = 240;
    private static final int MAX_FRAME_HEIGHT = 675;
    private String photo_path;
    private ProgressDialog mProgress;
    private Bitmap scanBitmap;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_capture);
        isOpen = false;
        prompt1 = (TextView) findViewById(R.id.prompt1);
        prompt2 = (TextView) findViewById(R.id.prompt2);
        photo = (Button) findViewById(R.id.photo);
        flash = (Button) findViewById(R.id.flash);
        myqrcode = (Button) findViewById(R.id.myqrcode);
        photo.setOnClickListener(this);
        flash.setOnClickListener(this);
        myqrcode.setOnClickListener(this);
        resetTextView();
        CameraManager.init(getApplication());
        initControl();

        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);
    }

    @Override
    public void onClick(View v)
    {
        switch (v.getId())
        {
            case R.id.photo:
                pickPictureFromAblum();
                break;
            case R.id.flash:
                turnLight();
                break;
            case R.id.myqrcode:
                Intent intent = new Intent(CaptureActivity.this, MyQrActivity.class);
                startActivity(intent);
                break;
        }
    }

    /*
     * 获取带二维码的相片进行扫描
     */
    public void pickPictureFromAblum()
    {
        // 打开手机中的相册
        Intent innerIntent = new Intent(Intent.ACTION_GET_CONTENT);
        innerIntent.setType("image/*");
        Intent wrapperIntent = Intent.createChooser(innerIntent, "选择二维码图片");
        this.startActivityForResult(wrapperIntent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (resultCode == RESULT_OK)
        {
            switch (requestCode)
            {
                case 1:
                    // 获取选中图片的路径
                    Cursor cursor = getContentResolver().query(data.getData(), null, null, null, null);
                    if (cursor.moveToFirst())
                    {
                        photo_path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                        Log.i("路径", photo_path);
                    }
                    cursor.close();

                    mProgress = new ProgressDialog(CaptureActivity.this);
                    mProgress.setMessage("正在扫描...");
                    mProgress.setCancelable(false);
                    mProgress.show();

                    new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            Result result = scanningImage(photo_path);
                            if (result != null)
                            {
                                Message m = mHandler.obtainMessage();
                                m.what = 1;
                                m.obj = result.getText();
                                mHandler.sendMessage(m);
                            } else
                            {
                                Message m = mHandler.obtainMessage();
                                m.what = 2;
                                m.obj = "Scan failed!";
                                mHandler.sendMessage(m);
                            }
                        }
                    }).start();
                    break;

                default:
                    break;
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    final Handler mHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case 1:
                    mProgress.dismiss();
                    String resultString = msg.obj.toString();
                    if (resultString.equals(""))
                    {
                        Toast.makeText(CaptureActivity.this, "扫描失败!", Toast.LENGTH_SHORT).show();
                    } else
                    {
                        Intent intent = new Intent(CaptureActivity.this, ResultActivity.class);
                        intent.putExtra("result", resultString);
                        startActivity(intent);
                    }
                    break;
                case 2:
                    mProgress.dismiss();
                    Toast.makeText(CaptureActivity.this, "解析错误！", Toast.LENGTH_LONG).show();
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }

    };

    /**
     * 扫描二维码图片的方法
     * 
     * 目前识别度不高，有待改进
     * 
     * @param path
     * @return
     */
    public Result scanningImage(String path)
    {
        if (TextUtils.isEmpty(path))
        {
            return null;
        }
        Hashtable<DecodeHintType, String> hints = new Hashtable<DecodeHintType, String>();
        hints.put(DecodeHintType.CHARACTER_SET, "UTF8"); // 设置二维码内容的编码

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; // 先获取原大小
        scanBitmap = BitmapFactory.decodeFile(path, options);
        options.inJustDecodeBounds = false; // 获取新的大小
        int sampleSize = (int) (options.outHeight / (float) 100);
        if (sampleSize <= 0)
            sampleSize = 1;
        options.inSampleSize = sampleSize;
        scanBitmap = BitmapFactory.decodeFile(path, options);
        RGBLuminanceSource source = new RGBLuminanceSource(scanBitmap);
        BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
        QRCodeReader reader = new QRCodeReader();
        try
        {
            return reader.decode(bitmap1, hints);

        } catch (NotFoundException e)
        {
            e.printStackTrace();
        } catch (ChecksumException e)
        {
            e.printStackTrace();
        } catch (FormatException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private void turnLight()
    {
        if (!isOpen)
        {
            parameters = CameraManager.get().camera.getParameters();
            parameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
            CameraManager.get().camera.setParameters(parameters);
            isOpen = true;
            Drawable dr = this.getResources().getDrawable(R.drawable.qrcode_scan_btn_flash_down);
            flash.setBackgroundDrawable(dr);
        } else
        {
            parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
            CameraManager.get().camera.setParameters(parameters);
            isOpen = false;
            Drawable dr = this.getResources().getDrawable(R.drawable.qrcode_scan_btn_flash_nor);
            flash.setBackgroundDrawable(dr);
        }
    }

    private void resetTextView()
    {
        WindowManager wm = this.getWindowManager();
        int screenWidth = wm.getDefaultDisplay().getWidth();
        int screenHeight = wm.getDefaultDisplay().getHeight();
        int height = findDesiredDimensionInRange(screenWidth, MIN_FRAME_HEIGHT, MAX_FRAME_HEIGHT);
        int topOffset = (screenHeight - height) / 2 + height;
        int topOffset2 = (screenHeight - height) / 100 * 47 + height;
        prompt1.setPadding(0, 0, 0, topOffset);
        prompt2.setPadding(0, 0, 0, topOffset2);
    }

    private static int findDesiredDimensionInRange(int resolution, int hardMin, int hardMax)
    {
        int dim = 3 * resolution / 5;
        if (dim < hardMin)
        {
            return hardMin;
        }
        if (dim > hardMax)
        {
            return hardMax;
        }
        return dim;
    }

    private void initControl()
    {
        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);

        btn_back = (Button) findViewById(R.id.btn_back);
        btn_back.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                CaptureActivity.this.finish();
            }
        });
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface)
        {
            initCamera(surfaceHolder);
        } else
        {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        decodeFormats = null;
        characterSet = null;

        playBeep = true;
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL)
        {
            playBeep = false;
        }
        initBeepSound();
        vibrate = true;
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (handler != null)
        {
            handler.quitSynchronously();
            handler = null;
        }
        CameraManager.get().closeDriver();
    }

    @Override
    public void onDestroy()
    {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    /**
     * @param result
     * @param barcode
     */
    @SuppressLint("NewApi")
    public void handleDecode(Result result, Bitmap barcode)
    {
        inactivityTimer.onActivity();
        playBeepSoundAndVibrate();

        String msg = result.getText();
        if (msg == null || "".equals(msg))
        {
            msg = "无法识别";
        } else
        {
            Intent intent = new Intent(CaptureActivity.this, ResultActivity.class);
            intent.putExtra("result", msg);
            startActivity(intent);
        }

    }

    private void initCamera(SurfaceHolder surfaceHolder)
    {
        try
        {
            CameraManager.get().openDriver(surfaceHolder);
        } catch (IOException ioe)
        {
            return;
        } catch (RuntimeException e)
        {
            return;
        }
        if (handler == null)
        {
            handler = new CaptureActivityHandler(this, decodeFormats, characterSet);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        if (!hasSurface)
        {
            hasSurface = true;
            initCamera(holder);
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        hasSurface = false;
    }

    public ViewfinderView getViewfinderView()
    {
        return viewfinderView;
    }

    public Handler getHandler()
    {
        return handler;
    }

    public void drawViewfinder()
    {
        viewfinderView.drawViewfinder();

    }

    /**
     * 扫描正确后的震动声音,如果感觉apk大了,可以删除
     */
    private void initBeepSound()
    {
        if (playBeep && mediaPlayer == null)
        {
            // The volume on STREAM_SYSTEM is not adjustable, and users found it
            // too loud,
            // so we now play on the music stream.
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnCompletionListener(beepListener);

            AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.beep);
            try
            {
                mediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
                file.close();
                mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                mediaPlayer.prepare();
            } catch (IOException e)
            {
                mediaPlayer = null;
            }
        }
    }

    private static final long VIBRATE_DURATION = 200L;

    private void playBeepSoundAndVibrate()
    {
        if (playBeep && mediaPlayer != null)
        {
            mediaPlayer.start();
        }
        if (vibrate)
        {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }

    /**
     * When the beep has finished playing, rewind to queue up another one.
     */
    private final OnCompletionListener beepListener = new OnCompletionListener()
    {
        public void onCompletion(MediaPlayer mediaPlayer)
        {
            mediaPlayer.seekTo(0);
        }
    };

}
