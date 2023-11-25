package com.meancoder.livetextfinder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.meancoder.livetextfinder.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements ImageAnalysis.Analyzer, ScaleGestureDetector.OnScaleGestureListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int MIN_STROKE_WIDTH = 4;
    private static final int MAX_STROKE_WIDTH = 16;
    ActivityMainBinding binding;
    int CAMERA_PERMISSION_CODE;
    private long lastInferenceStartTime = 0;
    private TextRecognizer recognizer;
    private String textToSearch = "";
    private boolean needUpdateGraphicOverlayImageSourceInfo = true;
    private boolean isTorchON = false;
    private float strokeWidth = MIN_STROKE_WIDTH;
    private int colorID = 1;
    private CameraControl cameraControl;
    private List<Rect> graphicRects = new ArrayList<>();
    private ScaleGestureDetector scaleDetector;
    private Camera camera;
    private PreferencesManager preferencesManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferencesManager = new PreferencesManager(this);
        if(preferencesManager.isFirstTimeLaunch()) {
            showTapTarget();
            preferencesManager.setFirstTimeLaunch(false);
        }
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CAMERA_PERMISSION_CODE = CameraPermissionHelper.requestCameraPermission(this);
        } else {
            startCamera();
            recognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        }
        scaleDetector = new ScaleGestureDetector(this, this);

        binding.helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTapTarget();
            }
        });
        binding.flashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isTorchON = !isTorchON;
                if (cameraControl != null) {
                    cameraControl.enableTorch(isTorchON);
                }
                binding.flashButton.setImageDrawable(getDrawable(isTorchON ? R.drawable.ic_baseline_flash_on_24 : R.drawable.ic_baseline_flash_off_24));
            }
        });
        binding.searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                graphicRects.clear();
                showTextDialog(MainActivity.this);
            }
        });
        binding.strokeWidthButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStrokeWidthDialog(MainActivity.this); //strokeWidth global variable is updated in this function
                ObjectGraphic.STROKE_WIDTH = strokeWidth;
//                binding.graphicOverlay.clear();
                binding.graphicOverlay.invalidate();
            }
        });
        binding.paintButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showColorChangeDialog(MainActivity.this); //colorID is changed here
                ObjectGraphic.colorID = colorID;
//                binding.graphicOverlay.clear();
                binding.graphicOverlay.postInvalidate();
            }
        });
        binding.viewFinder.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scaleDetector.onTouchEvent(event);
                return true;
            }
        });
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        if (textToSearch.length() == 0) {
            imageProxy.close();
            return;
        }
        @SuppressLint("UnsafeOptInUsageError") Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            if (needUpdateGraphicOverlayImageSourceInfo) {
                boolean isImageFlipped = false; // false for back camera
                int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                if (rotationDegrees == 0 || rotationDegrees == 180) {
                    binding.graphicOverlay.setImageSourceInfo(
                            imageProxy.getWidth(), imageProxy.getHeight(), isImageFlipped);
                } else {
                    binding.graphicOverlay.setImageSourceInfo(
                            imageProxy.getHeight(), imageProxy.getWidth(), isImageFlipped);
                }
                needUpdateGraphicOverlayImageSourceInfo = false;
            }

            Task<Text> result =
                    recognizer.process(image)
                            .addOnSuccessListener(new OnSuccessListener<Text>() {
                                @Override
                                public void onSuccess(Text visionText) {
                                    Log.i(TAG, "# graphics = " + binding.graphicOverlay.getObjectCount());
                                    String text = visionText.getText().toUpperCase();
                                    String textToSearchUpper = textToSearch.toUpperCase();
                                    if (text.contains(textToSearchUpper)) {
                                        //TODO: text has been found. locate and visualize the bounding boxes
                                        Log.i(TAG, textToSearch + " found in text");
                                        for(Text.TextBlock tb : visionText.getTextBlocks()){
                                            for(Text.Line line : tb.getLines()) {
                                                if(line.getText().toUpperCase().contains(textToSearchUpper)){
                                                    for (Text.Element e : line.getElements()){
                                                        if(Arrays.stream(textToSearchUpper.split(" ")).anyMatch(a -> a.equals(e.getText().toUpperCase()))) {
                                                            Rect lineBox = e.getBoundingBox();
                                                            ObjectGraphic object = new ObjectGraphic(binding.graphicOverlay, lineBox);
                                                            binding.graphicOverlay.add(object);
                                                            graphicRects.add(lineBox);
                                                            Log.i(TAG, "graphic object added");
                                                        }
                                                    }

                                                }
                                            }
                                        }
                                    } else {
                                        Log.i(TAG, "clearing overlay");
                                        binding.graphicOverlay.clear();
                                    }
                                }
                            })
                            .addOnFailureListener(
                                    new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
//                                            binding.graphicOverlay.clear();
                                        }
                                    })
                            .addOnCompleteListener(new OnCompleteListener<Text>() {
                                @Override
                                public void onComplete(@NonNull Task<Text> task) {
                                    imageProxy.close();
                                    binding.graphicOverlay.postInvalidate();
                                }
                            });
        }
    }
    @SuppressLint("UnsafeOptInUsageError")
    private void startCamera() {
        needUpdateGraphicOverlayImageSourceInfo=true;
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // Camera provider is now guaranteed to be available
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Set up the view finder use case to display camera preview
                Preview preview = new Preview.Builder().build();
                ImageAnalysis imageFrameAnalysis = new ImageAnalysis.Builder()
//                        .setTargetResolution(new Size(480, 640))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageFrameAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), this );

                // Choose the camera by requiring a lens facing
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();
                cameraProvider.unbindAll();
                // Attach use cases to the camera with the same lifecycle owner
                camera = cameraProvider.bindToLifecycle(
                        ((LifecycleOwner) this),
                        cameraSelector,
                        preview,
                        imageFrameAnalysis);
                cameraControl = camera.getCameraControl();
                // Connect the preview use case to the previewView
                preview.setSurfaceProvider(
                        binding.viewFinder.getSurfaceProvider());

            } catch (InterruptedException | ExecutionException e) {
                // Currently no exceptions thrown. cameraProviderFuture.get()
                // shouldn't block since the listener is being called, so no need to
                // handle InterruptedException.
            }
        }, ContextCompat.getMainExecutor(this));
    }



    private void showStrokeWidthDialog(Context ctx){
        AlertDialog.Builder dialog = new AlertDialog.Builder(ctx);
        dialog.setTitle("Stroke Width");
        dialog.setMessage("Slide the bar to change stroke width");
        TextView strokeWidthText = new TextView(ctx);
        strokeWidthText.setText("Width : " + (int) strokeWidth);

        SeekBar seekBar = new SeekBar(ctx);
        seekBar.setMin(MIN_STROKE_WIDTH);
        seekBar.setMax(MAX_STROKE_WIDTH);
        seekBar.setProgress((int) strokeWidth);
        seekBar.setPadding(2, 10,2,0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                strokeWidthText.setText("Width : " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        LinearLayout layoutName = new LinearLayout(this);
        layoutName.setOrientation(LinearLayout.VERTICAL);
        layoutName.addView(strokeWidthText);
        layoutName.addView(seekBar);
        layoutName.setPadding(50,20,50,20);
        dialog.setView(layoutName);
        dialog.setPositiveButton("Done", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                strokeWidth = seekBar.getProgress();
            }
        });
        dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        dialog.show();

    }

    private void showTextDialog(Context ctx) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(ctx);
        dialog.setTitle(R.string.alert_title);
        dialog.setMessage(R.string.alert_message);
        EditText editText = new EditText(ctx);
        editText.setText(textToSearch);
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        LinearLayout layoutName = new LinearLayout(this);
        layoutName.setOrientation(LinearLayout.VERTICAL);
        layoutName.addView(editText); // displays the user input bar
        dialog.setView(layoutName);
        dialog.setPositiveButton("Search", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                textToSearch = String.valueOf(editText.getText());
            }
        });
        dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        dialog.show();
    }

    private void showColorChangeDialog(Context ctx) {
        int parentLayoutHieght = 800;
        AlertDialog.Builder dialog = new AlertDialog.Builder(ctx);
        dialog.setTitle("Change Color");
        dialog.setMessage("Select any of the following box strokes colors");
        ScrollView scrollView = new ScrollView(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
               parentLayoutHieght );
        scrollView.setLayoutParams(layoutParams);

        LinearLayout linearLayout = new LinearLayout(this);
        LinearLayout.LayoutParams linearParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setLayoutParams(linearParams);
        scrollView.addView(linearLayout);
        for(int i = 0; i < ObjectGraphic.COLORS.length; i++) {
            int color = ObjectGraphic.COLORS[i];
            ImageView colorView = new ImageView(ctx);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT
                    , 100,1);
            params.setMargins(20,0,20,20);
            colorView.setLayoutParams(params);
            colorView.setBackgroundColor(color);
            int finalI = i;
            colorView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    colorID = finalI;
                    for(int ci=0; ci < linearLayout.getChildCount(); ci++) {
                        ((ImageView)linearLayout.getChildAt(ci)).setImageDrawable(null);
                    }
                    colorView.setImageDrawable(getDrawable(R.drawable.selected_view_background));
                }
            });
            linearLayout.addView(colorView);
        }
        LinearLayout parentlayout = new LinearLayout(ctx);
        parentlayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                parentLayoutHieght));
        parentlayout.addView(scrollView);
        dialog.setView(parentlayout);
        dialog.setPositiveButton("Select", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        dialog.show();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == CAMERA_PERMISSION_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    @Override
    public boolean onScale(@NonNull ScaleGestureDetector detector) {
        if(camera != null) {
            float scale = camera.getCameraInfo().getZoomState().getValue().getZoomRatio() * detector.getScaleFactor();
            cameraControl.setZoomRatio(scale);
            return true;
        }
        return false;
    }

    @Override
    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {

    }

    private void showTapTarget() {
        new TapTargetSequence(this).targets(
            buildTapTargetForView(binding.searchButton, "Se arch Button", "This button show a dialog box containing a textbox when clicked. Write whatever text you want to search for and click Search.", 60),
                buildTapTargetForView(binding.paintButton, "Paint Button", "This button show a list of colors you can choose from to highlight text. Select a color from the list and click Done.", 30),
                buildTapTargetForView(binding.strokeWidthButton, "Stroke Width Button", "This button allow you to change stroke with of the box used to highlight text.", 30),
                buildTapTargetForView(binding.flashButton, "Flash Button", "This button turns on/off camera flash when clicked", 30),
                buildTapTargetForView(binding.viewFinder, "Pinch to zoom", "Use pinch gesture anywhere on camera frame to zoom in or out.", 100),
                buildTapTargetForView(binding.helpButton, "Help Button", "Help is shown only once after you install this app. Click this help button to show help again.", 30)
        ).start();
    }
    private TapTarget buildTapTargetForView(View v, String title, String description, int radius) {
        return TapTarget.forView(v, title, description)
                .outerCircleColor(R.color.teal_700).outerCircleAlpha(0.9f)
                .targetCircleColor(R.color.white)
                .titleTextColor(R.color.white)
                .titleTextSize(18)
                .descriptionTextColor(R.color.white)
                .descriptionTextSize(14)
                .dimColor(R.color.black)
                .drawShadow(true)
                .cancelable(false)
                .tintTarget(true)
                .transparentTarget(true)
                .targetRadius(radius);
    }
}