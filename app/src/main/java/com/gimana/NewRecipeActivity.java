package com.gimana;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NewRecipeActivity extends AppCompatActivity {

    private static final String TAG = "NewRecipeActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 101;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";


    private PreviewView cameraPreviewView;
    private ImageView imageViewCaptured;
    private Button buttonTakePicture;
    private Button buttonGetRecipe;
    private Button buttonRetakePicture;
    private EditText editTextRecipeClue;
    private ProgressBar progressBarNewRecipe;

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private GeminiApiService geminiApiService;
    private RecipeDbHelper dbHelper;

    private Uri capturedImageUri;
    private byte[] capturedImageBytes;
    private String capturedImageMimeType;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_recipe);

        cameraPreviewView = findViewById(R.id.cameraPreviewView);
        imageViewCaptured = findViewById(R.id.imageViewCaptured);
        buttonTakePicture = findViewById(R.id.buttonTakePicture);
        buttonGetRecipe = findViewById(R.id.buttonGetRecipe);
        buttonRetakePicture = findViewById(R.id.buttonRetakePicture);
        editTextRecipeClue = findViewById(R.id.editTextRecipeClue);
        progressBarNewRecipe = findViewById(R.id.progressBarNewRecipe);

        cameraExecutor = Executors.newSingleThreadExecutor();
        geminiApiService = new GeminiApiService();
        dbHelper = new RecipeDbHelper(this);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        buttonTakePicture.setOnClickListener(v -> takePhoto());
        buttonGetRecipe.setOnClickListener(v -> processGetRecipe());
        buttonRetakePicture.setOnClickListener(v -> retakePicture());

        try {
            java.lang.reflect.Field apiKeyField = GeminiApiService.class.getDeclaredField("GEMINI_API_KEY");
            apiKeyField.setAccessible(true);
            String apiKey = (String) apiKeyField.get(null);
            if (apiKey == null || apiKey.equals("YOUR_GEMINI_API_KEY") || apiKey.isEmpty()) {
                Toast.makeText(this, getString(R.string.api_key_not_set_message), Toast.LENGTH_LONG).show();
                buttonGetRecipe.setEnabled(false);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Could not check API key via reflection", e);
        }


        updateUIState(UIState.CAMERA_PREVIEW);
    }

    private enum UIState {
        CAMERA_PREVIEW,
        IMAGE_CAPTURED,
        LOADING
    }

    private void updateUIState(UIState state) {
        switch (state) {
            case CAMERA_PREVIEW:
                cameraPreviewView.setVisibility(View.VISIBLE);
                buttonTakePicture.setVisibility(View.VISIBLE);
                imageViewCaptured.setVisibility(View.GONE);
                editTextRecipeClue.setVisibility(View.GONE);
                buttonGetRecipe.setVisibility(View.GONE);
                buttonRetakePicture.setVisibility(View.GONE);
                progressBarNewRecipe.setVisibility(View.GONE);
                capturedImageUri = null;
                capturedImageBytes = null;
                capturedImageMimeType = null;
                editTextRecipeClue.setText("");
                break;
            case IMAGE_CAPTURED:
                cameraPreviewView.setVisibility(View.GONE);
                buttonTakePicture.setVisibility(View.GONE);
                imageViewCaptured.setVisibility(View.VISIBLE);
                editTextRecipeClue.setVisibility(View.VISIBLE);
                buttonGetRecipe.setVisibility(View.VISIBLE);
                buttonRetakePicture.setVisibility(View.VISIBLE);
                progressBarNewRecipe.setVisibility(View.GONE);
                break;
            case LOADING:
                cameraPreviewView.setVisibility(View.GONE);
                buttonTakePicture.setVisibility(View.GONE);
                imageViewCaptured.setVisibility(View.VISIBLE);
                editTextRecipeClue.setVisibility(View.GONE);
                buttonGetRecipe.setVisibility(View.GONE);
                buttonRetakePicture.setVisibility(View.GONE);
                progressBarNewRecipe.setVisibility(View.VISIBLE);
                break;
        }
    }


    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreviewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);
            } catch (Exception e) {
                Log.e(TAG, "Use case binding failed", e);
                Toast.makeText(this, "Failed to start camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture is null, cannot take photo.");
            return;
        }

        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "GimanaApp");
        }


        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions
                .Builder(getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
                .build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                capturedImageUri = outputFileResults.getSavedUri();
                if (capturedImageUri != null) {
                    String msg = getString(R.string.image_saved_to) + capturedImageUri.toString();
                    Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, msg);

                    Glide.with(NewRecipeActivity.this)
                            .load(capturedImageUri)
                            .into(imageViewCaptured);

                    prepareImageForApi(capturedImageUri);
                    updateUIState(UIState.IMAGE_CAPTURED);
                } else {
                    Log.e(TAG, "Image URI is null after saving.");
                    Toast.makeText(NewRecipeActivity.this, "Error: Image URI null.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                Toast.makeText(getBaseContext(), String.format(getString(R.string.error_capturing_image), exception.getMessage()), Toast.LENGTH_SHORT).show();
                updateUIState(UIState.CAMERA_PREVIEW);
            }
        });
    }

    private void prepareImageForApi(Uri uri) {
        try {
            ContentResolver resolver = getContentResolver();
            InputStream inputStream = resolver.openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) inputStream.close();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String mimeType = resolver.getType(uri);
            if (mimeType == null) {
                String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
                if (extension != null) {
                    mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
                }
            }
            if (mimeType == null) mimeType = "image/jpeg";

            capturedImageMimeType = mimeType;

            Bitmap.CompressFormat format = Bitmap.CompressFormat.JPEG;
            if ("image/png".equals(mimeType)) {
                format = Bitmap.CompressFormat.PNG;
            } else if ("image/webp".equals(mimeType)) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    format = Bitmap.CompressFormat.WEBP_LOSSY;
                } else {
                    format = Bitmap.CompressFormat.JPEG;
                    capturedImageMimeType = "image/jpeg";
                }
            }

            bitmap.compress(format, 85, baos);
            capturedImageBytes = baos.toByteArray();
            baos.close();

        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found for URI: " + uri, e);
            Toast.makeText(this, "Error: Image file not found.", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Error reading image URI: " + uri, e);
            Toast.makeText(this, "Error reading image.", Toast.LENGTH_SHORT).show();
        }
    }


    private void retakePicture() {
        updateUIState(UIState.CAMERA_PREVIEW);
        startCamera();
    }


    private void processGetRecipe() {
        String clue = editTextRecipeClue.getText().toString().trim();

        if (capturedImageBytes == null || capturedImageMimeType == null || clue.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_take_a_picture_and_enter_a_clue), Toast.LENGTH_LONG).show();
            return;
        }

        updateUIState(UIState.LOADING);
        Toast.makeText(this, getString(R.string.generating_recipe), Toast.LENGTH_SHORT).show();

        cameraExecutor.execute(() -> {
            geminiApiService.getRecipeFromImage(capturedImageBytes, capturedImageMimeType, clue, new GeminiApiService.GeminiApiCallback() {
                @Override
                public void onSuccess(Recipe recipe) {
                    long recipeId = dbHelper.addRecipe(recipe, capturedImageUri);
                    recipe.setId(recipeId);

                    runOnUiThread(() -> {
                        updateUIState(UIState.IMAGE_CAPTURED);
                        Toast.makeText(NewRecipeActivity.this, getString(R.string.recipe_generated_and_saved), Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(NewRecipeActivity.this, RecipeDetailActivity.class);
                        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipeId);
                        startActivity(intent);
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> {
                        updateUIState(UIState.IMAGE_CAPTURED);
                        Log.e(TAG, "API Error", e);
                        Toast.makeText(NewRecipeActivity.this, String.format(getString(R.string.failed_to_generate_recipe), e.getMessage()), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }


    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}