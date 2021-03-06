package com.medetector.jmt.medetector;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

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

    private TextView mTextViewGuide;
    private CameraView mCameraView;
    private Button mButtonDetect;
    private Button mButtonGallery;
    private FrameLayout mDialogProgress;

    private boolean mIsBackPressBlocked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imagesearch);

        mTextViewGuide = (TextView)findViewById(R.id.textView_guide);
        mCameraView = (CameraView)findViewById(R.id.cameraView);
        mButtonDetect = (Button)findViewById(R.id.button_detect);
        mButtonGallery = (Button) findViewById(R.id.button_gallery);
        mDialogProgress = (FrameLayout)findViewById(R.id.dialog_progress);

        // 흐르는 텍스트 구현
        mTextViewGuide.setSelected(true);

        // 카메라 화면에서 터치하는 부분에 포커스가 잡히도록 설정
        mCameraView.setFocus(CameraKit.Constants.FOCUS_TAP_WITH_MARKER);

        // 클릭 리스너 세팅
        mButtonDetect.setOnClickListener(this);
        mButtonGallery.setOnClickListener(this);

        // 백키 막는 변수 초기화
        mIsBackPressBlocked = false;

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

        mIsBackPressBlocked = false;

        if(!mCameraView.isActivated())mCameraView.start();
        mCameraView.setVisibility(View.VISIBLE);
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
        int id = v.getId();

        switch(id) {
            case R.id.button_gallery:
                mIsBackPressBlocked = true;

                loadImageFromGallery();
                break;
            case R.id.button_detect:
                mIsBackPressBlocked = true;

                mCameraView.captureImage();
                mCameraView.setVisibility(View.INVISIBLE);
                mDialogProgress.setVisibility(View.VISIBLE);
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
            // 프로그래스 다이얼로그 보여줌
            mDialogProgress.setVisibility(View.VISIBLE);

            ArrayList<String> images = (ArrayList<String>) data.getSerializableExtra(ImageSelectorActivity.REQUEST_OUTPUT);

            // image decoded to bitmap, which can be recognized by tensorflow
            Bitmap bitmap = BitmapFactory.decodeFile(images.get(0));

            recognize_bitmap(bitmap);
        }
    }

    @Override
    public void onBackPressed() {
        // 이미지 캡쳐 및 분석 중에 백키를 눌러 나가버리면 앱이 죽는 버그 방지
        if(mIsBackPressBlocked){
            Toast.makeText(ImageSearchActivity.this, "Please Wait!", Toast.LENGTH_SHORT);
        }
        else{
            super.onBackPressed();
        }
    }

    ////// method //////

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

    // 갤러리로부터 이미지를 가져오는 메소드
    private void loadImageFromGallery() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Guide");
        builder.setMessage("Choose a clear picture of the medicine and crop the image to match the edge of the medicine.");
        builder.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        ImageSelectorActivity.start(ImageSearchActivity.this, 1, ImageSelectorActivity.MODE_SINGLE, false,true,true);
                    }
                });
        builder.show();

        //mCameraView.stop();
    }

    // 비트맵 인식해서 결과값 도출
    private void recognize_bitmap(Bitmap bitmap) {

        // 이미지 분석에 쓰일 비트맵 생성
        bitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);

        // 이미지 분석 결과
        final List<Classifier.Recognition> results = classifier.recognizeImage(bitmap);

        // 프로그래스 다이얼로그 숨김
        mDialogProgress.setVisibility(View.INVISIBLE);

        //Log.d("test", "result title : " + results.get(0).getTitle() + " / result confidence : " + results.get(0).getConfidence());

        // ResultActivity로 결과 전달
        Intent intent = new Intent(ImageSearchActivity.this, ResultActivity.class);
        intent.putExtra("result_title", results.get(0).getTitle());
        intent.putExtra("result_confidence", results.get(0).getConfidence());
        startActivity(intent);
    }
}
