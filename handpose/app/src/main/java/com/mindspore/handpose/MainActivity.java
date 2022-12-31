package com.mindspore.handpose;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.mindspore.handpose.utils.ModelManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String TAG = "MainActivity";

    private static final int REQUEST_PERMISSION = 0;

    private boolean isHasPermssion;

    private Bitmap originBitmap;

    private ModelManager modelManager;
    public boolean isRunningModel;
    private Handler mHandler;

    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private TextView textview;
    private String model_result_str;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
        modelManager = new ModelManager(this);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            isHasPermssion = true;
        } else {
            requestPermissions();
        }
    }

    private void init() {
        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);

        textview = (TextView) findViewById(R.id.text_view);
    }


    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE, Manifest.permission.CAMERA}, REQUEST_PERMISSION);
    }

    /**
     * Authority application result callback
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (REQUEST_PERMISSION == requestCode) {
            isHasPermssion = true;
        }
    }

    private void openGallay(int request) {
        Intent intent = new Intent(Intent.ACTION_PICK, null);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intent, request);
    }

    private void startRunningModel(boolean isDemo) {
        if (!isRunningModel) {
            if (originBitmap == null) {
                Toast.makeText(this, "Please select an original picture first", Toast.LENGTH_SHORT).show();
                return;
            }
            new Thread(() -> {
                isRunningModel = true;
                model_result_str = modelManager.execute(originBitmap);
                isRunningModel = false;
                Looper.prepare();
                mHandler = new MyHandler(MainActivity.this, isDemo);
                mHandler.sendEmptyMessage(1);
                Looper.loop();
            }).start();
        } else {
            Toast.makeText(this, "Previous Model still running", Toast.LENGTH_SHORT).show();
        }
    }

    private long last_time = 0;

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        mCamera = Camera.open();
        try {
            last_time = System.currentTimeMillis();
            mCamera.setDisplayOrientation(90);
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
            mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    // Do something with the preview frame data
                    long current_time = System.currentTimeMillis();
                    if(current_time - last_time > 2000){
                        last_time = current_time;

                        Camera.Size previewSize = camera.getParameters().getPreviewSize();
                        YuvImage yuvimage=new YuvImage(data, ImageFormat.NV21, previewSize.width, previewSize.height, null);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        yuvimage.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 80, baos);
                        byte[] jdata = baos.toByteArray();

                        originBitmap = BitmapFactory.decodeByteArray(jdata, 0, jdata.length);
                        startRunningModel(true);
                    }

                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }

    public void changeTextview(){
        runOnUiThread(() -> {
            textview.setText(model_result_str);
        });

    }

    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> weakReference;
        private boolean isDemo;

        public MyHandler(MainActivity activity, boolean demo) {
            weakReference = new WeakReference<>(activity);
            this.isDemo = demo;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            MainActivity activity = weakReference.get();
            if (msg.what == 1) {
                if (null != activity) {
                    activity.changeTextview();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        modelManager.free();
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }

    }

}