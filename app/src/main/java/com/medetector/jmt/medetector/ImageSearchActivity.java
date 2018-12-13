package com.medetector.jmt.medetector;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.wonderkiln.camerakit.CameraKit;
import com.wonderkiln.camerakit.CameraListener;
import com.wonderkiln.camerakit.CameraView;
import com.yongchun.library.view.ImageSelectorActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ImageSearchActivity extends Activity implements View.OnClickListener {
    // INPUT SIZE, MEAN, STD values are taken from label_image source
    private static final int INPUT_SIZE = 299;
    private static final int IMAGE_MEAN = 0;
    private static final float IMAGE_STD = 255.0f;
    private static final String INPUT_NAME = "Mul";
    private static final String OUTPUT_NAME = "final_result";

    private static final String MODEL_FILE = "file:///android_asset/graph.pb";
    private static final String LABEL_FILE = "file:///android_asset/labels.txt";

    private Classifier classifier;
    private Executor executor = Executors.newSingleThreadExecutor();

    private CameraView mCameraView;
    private Button btnDetect;
    private ImageView imgResult;
    private TextView txtResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imagesearch);

        Button btnGallery = (Button) findViewById(R.id.btnGallery);

        mCameraView = (CameraView)findViewById(R.id.cameraView);

        btnDetect = (Button)findViewById(R.id.btnDetect);

        imgResult = (ImageView)findViewById(R.id.imgResult);
        txtResult = (TextView)findViewById(R.id.txtResult);

        // btn events delegation
        btnGallery.setOnClickListener(this);
        btnDetect.setOnClickListener(this);

        // initialize tensorflow async
        initTensorFlowAndLoadModel();

        // permission check & request if needed
        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA
                ).withListener(new MultiplePermissionsListener() {
            @Override public void onPermissionsChecked(MultiplePermissionsReport report) {/* ... */}
            @Override public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {/* ... */}
        }).check();

        // cameraview library has its own permission check method
        mCameraView.setPermissions(CameraKit.Constants.PERMISSIONS_PICTURE);

        // invoke tensorflow inference when picture taken from camera
        mCameraView.setCameraListener(new CameraListener() {
            @Override
            public void onPictureTaken(byte[] picture) {
                super.onPictureTaken(picture);

                Bitmap bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.length);
                recognize_bitmap(bitmap);
            }
        });
    }

    @Override
    protected void onPause(){
        super.onPause();
        mCameraView.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!mCameraView.isActivated())mCameraView.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                classifier.close();
            }
        });
    }

    @Override
    public void onClick(View v) {
        // define which methods to call when buttons in view clicked
        int id = v.getId();

        switch(id) {
            case R.id.btnGallery:
                LoadImageFromGallery();
                break;
            case R.id.btnDetect:
                mCameraView.captureImage();
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // pass the selected image from image picker to tensorflow
        // image picker returns image(s) in arrayList

        if(resultCode == RESULT_OK && requestCode == ImageSelectorActivity.REQUEST_IMAGE){
            ArrayList<String> images = (ArrayList<String>) data.getSerializableExtra(ImageSelectorActivity.REQUEST_OUTPUT);

            // image decoded to bitmap, which can be recognized by tensorflow
            Bitmap bitmap = BitmapFactory.decodeFile(images.get(0));

            recognize_bitmap(bitmap);
        }
    }


    ////// method //////

    private void LoadImageFromGallery() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Guide");
        builder.setMessage("AlertDialog Content");
        builder.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        ImageSelectorActivity.start(ImageSearchActivity.this, 1, ImageSelectorActivity.MODE_SINGLE, false,true,true);
                    }
                });
        builder.show();

        //mCameraView.stop();
    }

    // recognize bitmap and get results
    private void recognize_bitmap(Bitmap bitmap) {

        // create a bitmap scaled to INPUT_SIZE
        bitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);

        // 이미지 분석 결과
        final List<Classifier.Recognition> results = classifier.recognizeImage(bitmap);

        // ResultActivity로 결과 전달
        Intent intent = new Intent(ImageSearchActivity.this, ResultActivity.class);
        intent.putExtra("result_title", results.get(0).getTitle());
        intent.putExtra("result_confidence", results.get(0).getConfidence());
        startActivity(intent);
    }

    private void initTensorFlowAndLoadModel() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    classifier = TensorFlowImageClassifier.create(
                            getAssets(),
                            MODEL_FILE,
                            LABEL_FILE,
                            INPUT_SIZE,
                            IMAGE_MEAN,
                            IMAGE_STD,
                            INPUT_NAME,
                            OUTPUT_NAME);
                } catch (final Exception e) {
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }
            }
        });
    }
}
