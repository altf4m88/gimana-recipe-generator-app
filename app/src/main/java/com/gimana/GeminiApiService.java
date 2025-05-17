package com.gimana;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class GeminiApiService {

    private static final String TAG = "GeminiApiService";

    // Replace with actual API Key (store securely, not hardcoded in production)
    private static final String GEMINI_API_KEY = "YOUR_KEY";

    private static final String MODEL_ID = "gemini-2.0-flash";

    private static final String GENERATE_CONTENT_API_METHOD = "generateContent";

    private static final String UPLOAD_BASE_URL = "https://generativelanguage.googleapis.com/upload/v1beta/files";
    private static final String GENERATE_CONTENT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_ID;

    private final OkHttpClient httpClient;
    private final Gson gson;

    public interface GeminiApiCallback {
        void onSuccess(Recipe recipe);
        void onFailure(Exception e);
    }

    public GeminiApiService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS) // File uploads and LLM generation can take time
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    // Step 1: Start Resumable Upload
    private void startUploadSession(byte[] imageBytes, String mimeType, String displayName, UploadSessionCallback callback) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(UPLOAD_BASE_URL).newBuilder();
        urlBuilder.addQueryParameter("key", GEMINI_API_KEY);

        String jsonBody = "{\"file\": {\"display_name\": \"" + displayName + "\"}}";
        RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .post(requestBody)
                .addHeader("X-Goog-Upload-Protocol", "resumable")
                .addHeader("X-Goog-Upload-Command", "start")
                .addHeader("X-Goog-Upload-Header-Content-Length", String.valueOf(imageBytes.length))
                .addHeader("X-Goog-Upload-Header-Content-Type", mimeType)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Start upload session failed", e);
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Start upload session unsuccessful: " + response.code() + " " + response.body().string());
                    callback.onFailure(new IOException("Start upload session failed: " + response.code() + " " + response.message()));
                    return;
                }
                String uploadUrl = response.header("X-Goog-Upload-URL");
                if (uploadUrl == null || uploadUrl.isEmpty()) {
                    Log.e(TAG, "X-Goog-Upload-URL not found in headers. Body: " + response.body().string());
                    callback.onFailure(new IOException("X-Goog-Upload-URL not found in start upload response headers."));
                    return;
                }
                Log.d(TAG, "Upload URL: " + uploadUrl);
                response.close();
                callback.onSuccess(uploadUrl);
            }
        });
    }

    // Step 2: Upload File Bytes and Finalize
    private void uploadFileBytes(String uploadUrl, byte[] imageBytes, String mimeType, FileUploadCallback callback) {
        RequestBody fileRequestBody = RequestBody.create(imageBytes, MediaType.parse(mimeType));

        Request request = new Request.Builder()
                .url(uploadUrl)
                .post(fileRequestBody)
                .addHeader("Content-Length", String.valueOf(imageBytes.length))
                 .addHeader("X-Goog-Upload-Offset", "0")
                .addHeader("X-Goog-Upload-Command", "upload, finalize")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Upload file bytes failed", e);
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        String errorBody = responseBody != null ? responseBody.string() : "Unknown error";
                        Log.e(TAG, "Upload file bytes unsuccessful: " + response.code() + " " + errorBody);
                        callback.onFailure(new IOException("Upload file bytes failed: " + response.code() + " " + response.message()));
                        return;
                    }
                    if (responseBody == null) {
                        callback.onFailure(new IOException("Response body was null after file upload."));
                        return;
                    }
                    String jsonResponse = responseBody.string();
                    Log.d(TAG, "File upload response: " + jsonResponse);

                    JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                    String fileUri = jsonObject.getAsJsonObject("file").get("uri").getAsString();
                    Log.d(TAG, "File URI: " + fileUri);
                    callback.onSuccess(fileUri);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing file upload response or response body null.", e);
                    callback.onFailure(e);
                }
            }
        });
    }


    // Main method to orchestrate upload and recipe generation
    public void getRecipeFromImage(byte[] imageBytes, String mimeType, String userClue, GeminiApiCallback callback) {
        String displayName = "food_image_" + System.currentTimeMillis();

        startUploadSession(imageBytes, mimeType, displayName, new UploadSessionCallback() {
            @Override
            public void onSuccess(String uploadUrl) {
                uploadFileBytes(uploadUrl, imageBytes, mimeType, new FileUploadCallback() {
                    @Override
                    public void onSuccess(String fileUri) {
                        generateRecipeContent(fileUri, mimeType, userClue, callback);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        callback.onFailure(e);
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }


    // Step 3: Generate Content (Recipe)
    private void generateRecipeContent(String fileUri, String imageMimeType, String userClue, GeminiApiCallback callback) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(GENERATE_CONTENT_BASE_URL + ":" + GENERATE_CONTENT_API_METHOD).newBuilder();
        urlBuilder.addQueryParameter("key", GEMINI_API_KEY);

        String requestJson = String.format(
                "{ " +
                        "  \"contents\": [ " +
                        "    { " +
                        "      \"role\": \"user\", " +
                        "      \"parts\": [ " +
                        "        { \"fileData\": { \"mimeType\": \"%s\", \"fileUri\": \"%s\" } }, " +
                        "        { \"text\": \"%s\" } " + // User's clue
                        "      ] " +
                        "    }, " +
                        "    { " +
                        "      \"role\": \"model\", " +
                        "      \"parts\": [ " +
                        "        { \"text\": \"{\\n  \\\"description\\\": \\\"Provide a detailed description of the dish here.\\\",\\n  \\\"ingredients\\\": \\\"## Ingredients\\n- Item 1\\n- Item 2\\n(Markdown format)\\\",\\n  \\\"how_to_make\\\": \\\"## Instructions\\n1. Step 1\\n2. Step 2\\n(Markdown format)\\\",\\n  \\\"tips\\\": \\\"- Tip 1\\n- Tip 2\\\"\\n}\" } " +
                        "      ] " +
                        "    } " +
                        "  ], " +
                        "  \"generationConfig\": { " +
                        "    \"responseMimeType\": \"application/json\", " +
                        "    \"responseSchema\": { " +
                        "      \"type\": \"object\", " +
                        "      \"properties\": { " +
                        "        \"description\": { \"type\": \"string\" }, " +
                        "        \"ingredients\": { \"type\": \"string\" }, " +
                        "        \"how_to_make\": { \"type\": \"string\" }, " +
                        "        \"tips\": { \"type\": \"string\" } " +
                        "      }, " +
                        "      \"required\": [\"description\", \"ingredients\", \"how_to_make\", \"tips\"] " +
                        "    } " +
                        "  } " +
                        "}",
                imageMimeType, fileUri, userClue.replace("\"", "\\\"")
        );


        RequestBody requestBody = RequestBody.create(requestJson, MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Generate content failed", e);
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        String errorBody = responseBody != null ? responseBody.string() : "Unknown error";
                        Log.e(TAG, "Generate content unsuccessful: " + response.code() + " " + errorBody);
                        callback.onFailure(new IOException("Generate content failed: " + response.code() + " " + response.message()));
                        return;
                    }

                    String jsonResponseString = responseBody.string();
                    Log.d(TAG, "Raw Generate content response: " + jsonResponseString);

                    JsonObject outerResponse = JsonParser.parseString(jsonResponseString).getAsJsonObject();
                    if (outerResponse.has("candidates") &&
                            outerResponse.getAsJsonArray("candidates").size() > 0 &&
                            outerResponse.getAsJsonArray("candidates").get(0).getAsJsonObject().has("content") &&
                            outerResponse.getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonObject("content").has("parts") &&
                            outerResponse.getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonObject("content").getAsJsonArray("parts").size() > 0 &&
                            outerResponse.getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject().has("text")) {

                        String recipeJson = outerResponse.getAsJsonArray("candidates").get(0).getAsJsonObject()
                                .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                                .get("text").getAsString();

                        Log.d(TAG, "Extracted Recipe JSON: " + recipeJson);
                        Recipe recipe = gson.fromJson(recipeJson, Recipe.class);
                        callback.onSuccess(recipe);
                    } else if (outerResponse.has("promptFeedback")) {
                        Log.e(TAG, "Prompt feedback received: " + outerResponse.get("promptFeedback").toString());
                        callback.onFailure(new IOException("Content generation blocked or failed due to prompt issues: " + outerResponse.get("promptFeedback").toString()));
                    }
                    else {
                        Log.e(TAG, "Could not find recipe text in response: " + jsonResponseString);
                        callback.onFailure(new IOException("Unexpected response structure from Gemini API."));
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing generate content response", e);
                    callback.onFailure(e);
                }
            }
        });
    }

    private interface UploadSessionCallback {
        void onSuccess(String uploadUrl);
        void onFailure(Exception e);
    }

    private interface FileUploadCallback {
        void onSuccess(String fileUri);
        void onFailure(Exception e);
    }
}