package com.mindspore.handpose.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import com.mindspore.MSTensor;
import com.mindspore.Model;
import com.mindspore.config.CpuBindMode;
import com.mindspore.config.DeviceType;
import com.mindspore.config.MSContext;
import com.mindspore.config.ModelType;


import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

public class ModelManager {
    private static final String TAG = "ModelManager";
    private static final String MOBILENET_HANDPOSE_MODEL = "mobilenet_handpose.ms"; // 模型名称
    private static final int imageSize = 224;
    public static final int NUM_CLASSES = 2;
    private static final float IMAGE_MEAN[] = new float[]{0.485F * 255, 0.456F * 255, 0.406F * 255};
    private static final float IMAGE_STD[] = new float[]{0.229F * 255, 0.224F * 255, 0.225F * 255};

    private final Context mContext;

    private Model model;

    public ModelManager(Context context) {
        mContext = context;
        init();
    }

    private MappedByteBuffer loadModel(Context context, String modelName) {
        FileInputStream fis = null;
        AssetFileDescriptor fileDescriptor = null;

        try {
            fileDescriptor = context.getAssets().openFd(modelName);
            fis = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = fis.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLen = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLen);
        } catch (IOException var24) {
            Log.e("MS_LITE", "Load model failed");
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException var23) {
                    Log.e("MS_LITE", "Close file failed");
                }
            }

            if (fileDescriptor != null) {
                try {
                    fileDescriptor.close();
                } catch (IOException var22) {
                    Log.e("MS_LITE", "Close fileDescriptor failed");
                }
            }

        }

        return null;
    }

    public void init() {
        model = new Model();
        // 创建上下文
        MSContext context = new MSContext();
        if (!context.init(2, CpuBindMode.MID_CPU, false)) {
            Log.e(TAG, "Init context failed");
            return;
        }
        if (!context.addDeviceInfo(DeviceType.DT_CPU, false, 0)) {
            Log.e(TAG, "Add device info failed");
            return;
        }
        MappedByteBuffer modelBuffer = loadModel(mContext, MOBILENET_HANDPOSE_MODEL);
        if(modelBuffer == null) {
            Log.e(TAG, "Load model failed");
            return;
        }
        // Create the MindSpore lite session.
        boolean ret = model.build(modelBuffer, ModelType.MT_MINDIR,context);
        if(!ret) {
            Log.e(TAG, "Build model failed");
        }
        Log.i(TAG, "Build model success");
    }

    public String execute(Bitmap bitmap) {
        // Set input tensor values.
        List<MSTensor> inputs = model.getInputs();
        if (inputs.size() != 1) {
            Log.e(TAG, "inputs.size() != 1");
            return "null";
        }

        // scaleBitmapAndKeepRatio的作用是
        Bitmap scaledBitmap = BitmapUtils.scaleBitmapAndKeepRatio(bitmap, imageSize, imageSize);
        ByteBuffer contentArray = BitmapUtils.bitmapToByteBuffer(scaledBitmap, imageSize, imageSize, IMAGE_MEAN, IMAGE_STD);

        MSTensor inTensor = inputs.get(0);
        inTensor.setData(contentArray);

        Log.i(TAG, "Set input image success!");

        // Run graph to infer results.
        if (!model.predict()) {
            Log.e(TAG, "Run graph failed");
            return "null";
        }

        Log.i(TAG, "Predict success!");

        // Get output tensor values.
        List<MSTensor> outputs = model.getOutputs();

        MSTensor output = outputs.get(0);
        if (output == null) {
            Log.e(TAG, "Output is null");
            return "null";
        }
        float[] results = output.getFloatData();
        Log.i(TAG,"ok:"+results[0]+",thumbup:"+results[1]);

        // 计算softmax
        // 1.求最大值
        float max_x = results[0];
        for(int i = 0;i<results.length;i++){
            if(results[i] > max_x){
                max_x = results[i];
            }
        }
        // 2.计算softmax
        float sum = 0;
        for(int i = 0;i<results.length;i++){
            results[i] = (float)Math.exp(results[i] - max_x);
            sum += results[i];
        }
        for(int i = 0;i<results.length;i++){
            results[i] = results[i] / sum;
        }

        String ret_str = "ok:"+String.format("%.2f", results[0])+"  ,  thumbup:"+String.format("%.2f", results[1]);

        return ret_str;
    }

    public void free() {
        model.free();
    }

}
