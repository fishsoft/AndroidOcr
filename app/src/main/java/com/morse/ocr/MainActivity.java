package com.morse.ocr;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TESSBASE_PATH = "/mnt/sdcard/ocr/";
    private static final String DEFAULT_LANGUAGE = "eng";
    private static final String CHINESE_LANGUAGE = "chi_sim";
    private static final String IMAGE_PATH_IS_NULL = "图片地址为空";
    private static final String TAG = "ocr";
    private static final int REQUEST_CODE_PICK_IMAGE = 0x1000;
    private static final int REQUEST_CODE_CAPTURE_CAMEIA = 0x1001;
    private String path = null;
    private String code;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, getInnerSDCardPath() + "");
        Log.d(TAG, getExtSDCardPath().toString() + "");
    }

    public void ocr(View view) {
        getImageFromAlbum();
//        getImageFromCamera();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if (RESULT_OK != resultCode) {
            return;
        }
        if (REQUEST_CODE_PICK_IMAGE == requestCode) {
            Bitmap bm = null;
            try {
                Uri originalUri = data.getData();        //获得图片的uri
                ContentResolver resolver = getContentResolver();
                bm = MediaStore.Images.Media.getBitmap(resolver, originalUri);
                //    这里开始的第二部分，获取图片的路径：
                String[] proj = {MediaStore.Images.Media.DATA};
                //好像是android多媒体数据库的封装接口，具体的看Android文档
                Cursor cursor = managedQuery(originalUri, proj, null, null, null);
                //按我个人理解 这个是获得用户选择的图片的索引值
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                //将光标移至开头 ，这个很重要，不小心很容易引起越界
                cursor.moveToFirst();

                //最后根据索引值获取图片路径
                path = cursor.getString(column_index);
            } catch (IOException e) {

                Log.e("TAG-->Error", e.toString());

            }
        }

        if (REQUEST_CODE_CAPTURE_CAMEIA == requestCode) {

        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    code = parseImageToString(path);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, code + "", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * 识别图片中文字,需要放入异步线程中进行执行
     *
     * @param imagePath
     * @return
     * @throws IOException
     */
    public String parseImageToString(String imagePath) throws IOException {
        // 检验图片地址是否正确
        if (TextUtils.isEmpty(imagePath)) {
            return IMAGE_PATH_IS_NULL;
        }

        // 获取Bitmap
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 2;
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
        if (null == bitmap) {
            return null;
        }

        // 图片旋转角度
        int rotate = 0;

        ExifInterface exif = new ExifInterface(imagePath);

        // 先获取当前图像的方向，判断是否需要旋转
        int imageOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL);

        Log.i(TAG, "Current image orientation is " + imageOrientation);

        switch (imageOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotate = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotate = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotate = 270;
                break;
            default:
                break;
        }

        Log.i(TAG, "Current image need rotate: " + rotate);

        // 获取当前图片的宽和高
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        // 使用Matrix对图片进行处理
        Matrix mtx = new Matrix();
        mtx.preRotate(rotate);

        // 旋转图片
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        // 开始调用Tess函数对图像进行识别
        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.setDebug(true);
        // 使用默认语言初始化BaseApi
        baseApi.init(TESSBASE_PATH, CHINESE_LANGUAGE);
        baseApi.setImage(bitmap);

        // 获取返回值
        String recognizedText = baseApi.getUTF8Text();
        baseApi.end();
        return recognizedText;
    }

    protected void getImageFromAlbum() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");//相片类型
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
    }

    protected void getImageFromCamera() {
        String state = Environment.getExternalStorageState();
        if (state.equals(Environment.MEDIA_MOUNTED)) {
            Intent getImageByCamera = new Intent("android.media.action.IMAGE_CAPTURE");
            startActivityForResult(getImageByCamera, REQUEST_CODE_CAPTURE_CAMEIA);
        } else {
            Toast.makeText(getApplicationContext(), "请确认已经插入SD卡", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 获取内置SD卡路径
     *
     * @return
     */
    public String getInnerSDCardPath() {
        return Environment.getExternalStorageDirectory().getPath();
    }

    /**
     * 获取外置SD卡路径
     *
     * @return 应该就一条记录或空
     */
    public List<String> getExtSDCardPath() {
        List<String> lResult = new ArrayList<String>();
        try {
            Runtime rt = Runtime.getRuntime();
            Process proc = rt.exec("mount");
            InputStream is = proc.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("extSdCard")) {
                    String[] arr = line.split(" ");
                    String path = arr[1];
                    File file = new File(path);
                    if (file.isDirectory()) {
                        lResult.add(path);
                    }
                }
            }
            isr.close();
        } catch (Exception e) {
        }
        return lResult;
    }
}
