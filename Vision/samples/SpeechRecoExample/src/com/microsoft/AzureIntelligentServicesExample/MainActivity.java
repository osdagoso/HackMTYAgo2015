/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license.
 * //
 * Microsoft Cognitive Services (formerly Project Oxford): https://www.microsoft.com/cognitive-services
 * //
 * Microsoft Cognitive Services (formerly Project Oxford) GitHub:
 * https://github.com/Microsoft/Cognitive-Speech-STT-Android
 * //
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 * //
 * MIT License:
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * //
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * //
 * THE SOFTWARE IS PROVIDED ""AS IS"", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.microsoft.AzureIntelligentServicesExample;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.FaceAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

import com.microsoft.projectoxford.speechrecognition.DataRecognitionClient;
import com.microsoft.projectoxford.speechrecognition.ISpeechRecognitionServerEvents;
import com.microsoft.projectoxford.speechrecognition.MicrophoneRecognitionClient;
import com.microsoft.projectoxford.speechrecognition.RecognitionResult;
import com.microsoft.projectoxford.speechrecognition.SpeechRecognitionMode;
import com.microsoft.projectoxford.speechrecognition.SpeechRecognitionServiceFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements ISpeechRecognitionServerEvents, TextToSpeech.OnInitListener
{
    private static final String CLOUD_VISION_API_KEY = "AIzaSyB_KZTcCduNsOB8CVsAeqqKgeJqz3nPYwM";
    public static final String FILE_NAME = "image.bmp";
    private static final String TAG = MainActivity.class.getSimpleName();

    private int MY_DATA_CHECK_CODE = 0;
    private TextToSpeech myTTS;

    private Camera mCamera = null;
    private CameraView mCameraView = null;

    private BatchAnnotateImagesResponse bairResponse = new BatchAnnotateImagesResponse();
    private boolean isTextAvailable = false;
    private boolean isImageScanned = false;
    private boolean isTextScanned = false;

    private boolean canListen = true;

    MicrophoneRecognitionClient micClient = null;

    /**
     * Gets the primary subscription key
     */
    public String getPrimaryKey() {
        return this.getString(R.string.primaryKey);
    }

    /**
     * Gets the secondary subscription key
     */
    public String getSecondaryKey() {
        return this.getString(R.string.secondaryKey);
    }

    /**
     * Gets the LUIS application identifier.
     * @return The LUIS application identifier.
     */
    private String getLuisAppId() {
        return this.getString(R.string.luisAppID);
    }

    /**
     * Gets the LUIS subscription identifier.
     * @return The LUIS subscription identifier.
     */
    private String getLuisSubscriptionID() {
        return this.getString(R.string.luisSubscriptionID);
    }

    /**
     * Gets the default locale.
     * @return The default locale.
     */
    private String getDefaultLocale() {
        return "en-us";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCamera = getCameraInstance();

        if(mCamera != null) {
            mCameraView = new CameraView(this, mCamera); //create a SurfaceView to show camera data
            FrameLayout camera_view = (FrameLayout)findViewById(R.id.camera_view);
            camera_view.addView(mCameraView); //add the SurfaceView to the layout

            Camera.Parameters params = mCamera.getParameters();
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            mCamera.setParameters(params);
        }

        Intent checkTTSIntent = new Intent();
        checkTTSIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkTTSIntent, MY_DATA_CHECK_CODE);

        if (getString(R.string.primaryKey).startsWith("Please")) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.add_subscription_key_tip_title))
                    .setMessage(getString(R.string.add_subscription_key_tip))
                    .setCancelable(false)
                    .show();
        }

        final MainActivity This = this;
        This.StartListening();
    }

    private Camera getCameraInstance() {
        Camera camera = null;
        try {
            camera = Camera.open();
        } catch (Exception e) {
            Log.d("ERROR", "Failed to get camera: " + e.getMessage());
        }
        return camera;
    }

    Camera.PictureCallback mPicture = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            File pictureFile = getOutputMediaFile();
            if (pictureFile == null) {
                return;
            }
            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
                uploadImage(Uri.fromFile(pictureFile));
            } catch (FileNotFoundException e) {
            } catch (IOException e) {
            }
            mCamera.startPreview();
        }
    };

    private static File getOutputMediaFile() {
        File mediaStorageDir = new File(
                Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Vision");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("Vision", "failed to create directory");
                return null;
            }
        }
        // Create a media file name
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + FILE_NAME);

        return mediaFile;
    }

    public void onInit(int initStatus) {
        if (initStatus == TextToSpeech.SUCCESS) {
            myTTS.setLanguage(Locale.US);

            myTTS.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String s) {
                }

                @Override
                public void onDone(String s) {
                    System.out.println("SSSSSSSSSS:" + s);
                    if (micClient != null)
                    {
                        micClient.endMicAndRecognition();
                    }
                    StartListening();
                    Log.d(TAG, "Lol");
                }

                @Override
                public void onError(String s) {
                }
            });
        }
        else if (initStatus == TextToSpeech.ERROR) {
            // do nothing
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MY_DATA_CHECK_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                myTTS = new TextToSpeech(this, this);
            }
            else {
                Intent installTTSIntent = new Intent();
                installTTSIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installTTSIntent);
            }
        }
    }

    public void Speak(String words) {
        if (words.length() > 0) {
            speakWords(words);
        }
    }

    private void speakWords(String speech) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ttsGreater21(speech);
        } else {
            ttsUnder20(speech);
        }
    }

    @SuppressWarnings("deprecation")
    private void ttsUnder20(String text) {
        HashMap<String, String> map = new HashMap<>();
        map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "MessageId");
        myTTS.speak(text, TextToSpeech.QUEUE_FLUSH, map);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void ttsGreater21(String text) {
        String utteranceId = this.hashCode() + "";
        myTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    /**
     * Handles the event of starting the microphone.
     */
    private void StartListening() {

        while (this.micClient == null) {
            this.micClient = SpeechRecognitionServiceFactory.createMicrophoneClientWithIntent(
                    this,
                    this.getDefaultLocale(),
                    this,
                    this.getPrimaryKey(),
                    this.getSecondaryKey(),
                    this.getLuisAppId(),
                    this.getLuisSubscriptionID());
        }

        if (canListen) {
            this.micClient.startMicAndRecognition();
        }
    }

    public void onFinalResponseReceived(final RecognitionResult response) {
        if (this.micClient != null) {
            // we got the final result, so it we can end the mic reco.  No need to do this
            // for dataReco, since we already called endAudio() on it as soon as we were done
            // sending all the data.
            this.micClient.endMicAndRecognition();
        }

        String instruction = "";
        if (response.Results.length > 0) {
            instruction = response.Results[0].DisplayText;
        }

        if (instruction.length() > 0) {
            instruction = instruction.substring(0, instruction.length() - 1);
            if (instruction.equals("Hi") ||
                    instruction.equals("Hello") ||
                    instruction.equals("Hey")) {
                int iRand = (1 + (int)(Math.random() * 9));
                if(iRand <= 3) {
                    Speak("Hi");
                }
                else if (iRand > 3 && iRand <= 6){
                    Speak("Hey");
                }
                else {
                    Speak("Hello");
                }
            }
            else if (instruction.equals("Take a photo") ||
                    instruction.equals("Take a picture") ||
                    instruction.equals("Take photo") ||
                    instruction.equals("Take picture")) {
                Speak("Taking picture. Scanning.");
                isTextAvailable = false;
                mCamera.takePicture(null, null, mPicture);
                canListen = false;
            }
            else if (instruction.equals("Help")){
                Speak("App can help you familiarize with your surroundings. Say take a photo to" +
                        "snap a picture and get information about it. Say repeat and I'll say it" +
                        "again. Say help to hear this again.");
            }
            else if (instruction.equals("Repeat") ||
                    instruction.equals("Repeat that") ||
                    instruction.equals("Can you repeat that")) {
                if (isImageScanned) {
                    canListen = false;
                    convertResponseToString(bairResponse);
                }
                else if (isTextScanned) {
                    canListen = false;
                    extractTextFromResponse(bairResponse);
                }
                else {
                    Speak("I'm sorry. There's no information for me to repeat.");
                }
            }
            else if (instruction.equals("Bro")) {
                Speak("I love you, Bro.");
            }
            else if (instruction.equals("I am your father")) {
                Speak("No!");
            }
            else if (instruction.equals("Who are you") ||
                    instruction.equals("What's your name")) {
                Speak("A robot has no name.");
            }
            else if (instruction.equals("Are you religious")) {
                Speak("Lord of light, cast your light upon us. For the night is dark and full of" +
                        "terrors.");
            }
            else if (instruction.equals("Shame")) {
                Speak("Shame. Shame. Tee ling. Tee ling.");
            }
            else if (instruction.equals("What does Marcellus Wallace look like")) {
                Speak("Does he look like a bee tch");
            }
            else if (instruction.equals("Thanks") ||
                    instruction.equals("Thank you")) {
                Speak("You're welcome.");
            }
            else if ((isTextAvailable && (instruction.equals("Yes") ||
                    instruction.equals("Okay") ||
                    instruction.equals("Yes please"))) ||
                    instruction.equals("Read the text")) {
                if (isTextAvailable) {
                    canListen = false;
                    isTextScanned = true;
                    isImageScanned = false;
                    extractTextFromResponse(bairResponse);
                }
                else {
                    Speak("I'm sorry. There's no text for me to read at the moment.");
                }
            }
            else if (isTextAvailable && (instruction.equals("No") || instruction.equals("No thanks")
                    || instruction.equals("No, thank you"))) {
                Speak("Understood.");
                isTextScanned = false;
                isImageScanned = false;
                isTextAvailable = false;
            }
            else if (instruction.equals("Close application") ||
                    instruction.equals("Goodbye") ||
                    instruction.equals("Bye") ||
                    instruction.equals("Good bye")){
                int iRand = (1 + (int)(Math.random() * 9));
                if (iRand <= 5) {
                    Speak("Goodbye.");
                }
                else {
                    Speak("Bye.");
                }
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e)
                {
                    // do nothing
                }
                this.finish();
                System.exit(0);
            }
            else {
                Speak("I'm sorry. I don't understand what you mean.");
            }
        } else {
            StartListening();
        }
    }

    /**
     * Called when a final response is received and its intent is parsed
     */
    public void onIntentReceived(final String payload) {
        // Empty. We don't need the payload, but we need this method to be declared.
    }

    public void onPartialResponseReceived(final String response) {
        // Empty. We don't need partial responses, but we need this method to be declared.
    }

    public void onError(final int errorCode, final String response) {
        // do nothing
    }

    /**
     * Called when the microphone status has changed.
     * @param recording The current recording state
     */
    public void onAudioEvent(boolean recording) {
        // do nothing. mic status never changes, but we need this method to be declared.
    }

    /*
     * Speech recognition with data (for example from a file or audio source).
     * The data is broken up into buffers and each buffer is sent to the Speech Recognition Service.
     * No modification is done to the buffers, so the user can apply their
     * own VAD (Voice Activation Detection) or Silence Detection
     *
     * @param dataClient
     * @param recoMode
     * @param filename
     */
    private class RecognitionTask extends AsyncTask<Void, Void, Void> {
        DataRecognitionClient dataClient;
        SpeechRecognitionMode recoMode;
        String filename;

        RecognitionTask(DataRecognitionClient dataClient, SpeechRecognitionMode recoMode, String filename) {
            this.dataClient = dataClient;
            this.recoMode = recoMode;
            this.filename = filename;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                // Note for wave files, we can just send data from the file right to the server.
                // In the case you are not an audio file in wave format, and instead you have just
                // raw data (for example audio coming over bluetooth), then before sending up any
                // audio data, you must first send up an SpeechAudioFormat descriptor to describe
                // the layout and format of your raw audio data via DataRecognitionClient's sendAudioFormat() method.

                String filename = recoMode == SpeechRecognitionMode.ShortPhrase ?
                        "whatstheweatherlike.wav" : "batman.wav";
                InputStream fileStream = getAssets().open(filename);
                int bytesRead = 0;
                byte[] buffer = new byte[1024];

                do {
                    // Get  Audio data to send into byte buffer.
                    bytesRead = fileStream.read(buffer);

                    if (bytesRead > -1) {
                        // Send of audio data to service.
                        dataClient.sendAudio(buffer, bytesRead);
                    }
                } while (bytesRead > 0);

            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            finally {
                dataClient.endAudio();
            }

            return null;
        }
    }

    public void uploadImage(Uri uri) {
        if (uri != null) {
            try {
                // scale the image to save on bandwidth
                Bitmap bitmap =
                        scaleBitmapDown(
                                MediaStore.Images.Media.getBitmap(getContentResolver(), uri),
                                1200);

                callCloudVision(bitmap);
                //mMainImage.setImageBitmap(bitmap);

            } catch (IOException e) {
                Log.d(TAG, "Image picking failed because " + e.getMessage());
                Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
            }
        } else {
            Log.d(TAG, "Image picker gave us a null image.");
            Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
        }
    }

    private void callCloudVision(final Bitmap bitmap) throws IOException {
        // Switch text to loading
        //fab.setVisibility(View.GONE);
        //mImageDetails.setText(R.string.loading_message);

        // Do the real work in an async task, because we need to use the network anyway
        new AsyncTask<Object, Void, String>() {
            @Override
            protected String doInBackground(Object... params) {
                try {
                    HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
                    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                    Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
                    builder.setVisionRequestInitializer(new
                            VisionRequestInitializer(CLOUD_VISION_API_KEY));
                    Vision vision = builder.build();

                    BatchAnnotateImagesRequest batchAnnotateImagesRequest =
                            new BatchAnnotateImagesRequest();
                    batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
                        AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

                        // Add the image
                        Image base64EncodedImage = new Image();
                        // Convert the bitmap to a JPEG
                        // Just in case it's a format that Android understands but Cloud Vision
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                        byte[] imageBytes = byteArrayOutputStream.toByteArray();

                        // Base64 encode the JPEG
                        base64EncodedImage.encodeContent(imageBytes);
                        annotateImageRequest.setImage(base64EncodedImage);

                        // add the features we want
                        annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                            Feature labelDetection = new Feature();
                            Feature logoDetection = new Feature();
                            Feature textDetection = new Feature();
                            Feature faceDetection = new Feature();

                            labelDetection.setType("LABEL_DETECTION");
                            labelDetection.setMaxResults(5);

                            logoDetection.setType("LOGO_DETECTION");
                            labelDetection.setMaxResults(5);

                            textDetection.setType("TEXT_DETECTION");
                            textDetection.setMaxResults(5);

                            faceDetection.setType("FACE_DETECTION");
                            textDetection.setMaxResults(5);

                            add(labelDetection);
                            add(logoDetection);
                            add(textDetection);
                            add(faceDetection);
                        }});

                        // Add the list of one thing to the request
                        add(annotateImageRequest);
                    }});

                    Vision.Images.Annotate annotateRequest =
                            vision.images().annotate(batchAnnotateImagesRequest);
                    // Due to a bug: requests to Vision API containing large images fail when GZipped.
                    annotateRequest.setDisableGZipContent(true);
                    Log.d(TAG, "created Cloud Vision request object, sending request");

                    Log.d(TAG, "Ya escaneo.");
                    bairResponse = annotateRequest.execute();
                    Log.d(TAG, "response received and stored");
                    return convertResponseToString(bairResponse);
                    //return "Use the floating action button to select an image.";
                    //return "Pato";

                } catch (GoogleJsonResponseException e) {
                    Log.d(TAG, "failed to make API request because " + e.getContent());
                } catch (IOException e) {
                    Log.d(TAG, "failed to make API request because of other IOException " +
                            e.getMessage());
                }
                return "Cloud Vision API request failed. Check logs for details.";
            }

            protected void onPostExecute(String result) {
                //repeat.setVisibility(View.VISIBLE);
                //next.setVisibility(View.VISIBLE);

                //mImageDetails.setText(result);
            }
        }.execute();
    }

    public Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }

    // Function that checks if there's readable text in the last analyzed image
    private boolean isTextinResponse(BatchAnnotateImagesResponse response) {
        List<EntityAnnotation> texts = response.getResponses().get(0).getTextAnnotations();
        if (texts != null) {
            return true;
        }
        else {
            return false;
        }
    }

    // Function that interprets the analyzed image's data and stores it in a string
    private String convertResponseToString(BatchAnnotateImagesResponse response) {
        String message = "";

        boolean bSureLabel = false;
        boolean bUnsureLabel = false;

        // Store found labels, logos and faces in lists
        List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();
        List<EntityAnnotation> logos = response.getResponses().get(0).getLogoAnnotations();
        List<FaceAnnotation> faces = response.getResponses().get(0).getFaceAnnotations();

        if (labels != null) {
            for (EntityAnnotation label : labels) {
                if (label.getScore() > 0.75) {
                    if (!bSureLabel) {
                        //message += "This picture is about ";
                        message += "This picture is about " + label.getDescription();
                        //message += label.getDescription();
                        bSureLabel = true;
                    }
                    else {
                        message += ", ";
                        message += label.getDescription();
                        //Speak(label.getDescription());
                    }
                }
                else {
                    if (!bUnsureLabel) {
                        if (bSureLabel) {
                            //message += "\nIt may also be about ";
                            message +=". It may also be about ";
                        } else {
                            message += "This picture may be about ";
                            //Speak("This picture may be about ");
                        }
                        message += label.getDescription();
                        //Speak (label.getDescription());
                        bUnsureLabel = true;
                    }
                    else {
                        message += ", ";
                        message += label.getDescription();
                        //Speak (label.getDescription());
                    }
                }
            }
            //message += "\n";
        } else {
            message += "I'm sorry, I can't analyze this photo.";
            //Speak ("I'm sorry, I can't analyze this photo.");
            Speak(message);
            canListen = true;
            return message;
        }

        if (logos != null) {
            //message += "\n\nI found ";
            message += ". I found ";
            if (logos.size() > 1) {
                message += String.format("&i logos ", logos.size());
                //Speak(String.format("&i logos", logos.size()));
            } else {
                message += "1 logo ";
                //Speak("1 logo");
            }
            for (EntityAnnotation logo : logos) {
                message += ", ";
                message += logo.getDescription();
                //Speak(logo.getDescription());
                //message += "\n";
            }
        }
        /*
        if (faces != null) {
            message += "\n\nI found ";
            if (faces.size() > 1) {
                message += String.format("%d faces: \n\n", faces.size());
            } else {
                message += "1 face:\n\n";
            }
            for (FaceAnnotation face : faces) {
                message += "A ";
                if (face.getJoyLikelihood().equals("VERY_LIKELY") || face.getJoyLikelihood().
                        equals("LIKELY")) {
                    message += "happy, ";
                } else if (face.getJoyLikelihood().equals("POSSIBLY")) {
                    message += "possibly happy, ";
                }
                if (face.getSorrowLikelihood().equals("VERY_LIKELY") || face.getSorrowLikelihood().
                        equals("LIKELY")) {
                    message += "sad, ";
                } else if (face.getSorrowLikelihood().equals("POSSIBLY")) {
                    message += "possibly sad, ";
                }
                if (face.getAngerLikelihood().equals("VERY_LIKELY") || face.getAngerLikelihood().
                        equals("LIKELY")) {
                    message += "furious, ";
                } else if (face.getAngerLikelihood().equals("POSSIBLY")) {
                    message += "possibly angry, ";
                }
                if (face.getSurpriseLikelihood().equals("VERY_LIKELY") || face.
                        getSurpriseLikelihood().equals("LIKELY")) {
                    message += "surprised, ";
                } else if (face.getSurpriseLikelihood().equals("POSSIBLY")) {
                    message += "possibly surprised, ";
                }
                message += "person.\n";
            }
        }
        */

        if (isTextinResponse(response)) {
            message += ". I've also found some text. Would you like me to read it?";
            //Speak("I've also found some text. Would you like me to read it?");
            isTextAvailable = true;
        }

        Speak(message);
        isImageScanned = true;
        canListen = true;

        return message;
    }

    // Function that extracts text found in an image and stores it in a string
    private String extractTextFromResponse(BatchAnnotateImagesResponse response) {
        String message = "Reading the text... ";
        //Speak("Reading the text");

        // Store the found text and its individual words in a list
        List<EntityAnnotation> texts = response.getResponses().get(0).getTextAnnotations();

        if (texts != null) {
            EntityAnnotation text = texts.get(0);
            message += text.getDescription();
        } else {
            message += "nothing";
        }

        Speak(message);
        canListen = true;

        return message;
    }
}
