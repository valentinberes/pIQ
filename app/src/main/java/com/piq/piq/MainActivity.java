package com.piq.piq;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.StreamingContent;
import com.google.api.client.util.Strings;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.FaceAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    static final int REQUEST_CAMERA = 1;
    static final int INT_MAX = 2147483647;

    static final String CLOUD_VISION_API_KEY = "AIzaSyDRaprkEKucImI202_eR34L56LshU7HTIo";

    ImageView imageView;
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize the contents of the layout
        Button button = (Button) findViewById(R.id.button);
        imageView = (ImageView) findViewById(R.id.imageView);
        textView = (TextView) findViewById(R.id.textView);

        // Request camera permission from user
        // Shamelessly taken from
        // http://stackoverflow.com/questions/36349130/how-to-access-camera-in-android-6-0-marshmallow
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA);
        }

        // Button opens camera on click
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(cameraIntent, REQUEST_CAMERA);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CAMERA && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            imageView.setImageBitmap(imageBitmap);
            try {
                // TODO: Call callCloudVision on a higher res version of imageBitmap
                callCloudVision(imageBitmap);
            } catch (IOException e) {
                // Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }

        }
    }

    private void callCloudVision(final Bitmap bitmap) throws IOException {
        // Switch text to loading
        textView.setText(R.string.loading_message);

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
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                        byte[] imageBytes = byteArrayOutputStream.toByteArray();

                        // Base64 encode the JPEG
                        base64EncodedImage.encodeContent(imageBytes);
                        annotateImageRequest.setImage(base64EncodedImage);

                        // add the features we want
                        annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                            ///* OCR
                            Feature textDetection = new Feature();
                            textDetection.setType("TEXT_DETECTION");
                            textDetection.setMaxResults(INT_MAX);
                            add(textDetection);
                            //*/
                            ///* LABEL
                            Feature labelDetection = new Feature();
                            labelDetection.setType("LABEL_DETECTION");
                            labelDetection.setMaxResults(20);
                            add(labelDetection);
                            //*/
                            ///* LOGOS
                            Feature logoDetection = new Feature();
                            logoDetection.setType("LOGO_DETECTION");
                            logoDetection.setMaxResults(10);
                            add(logoDetection);
                            //*/
                            ///* FACES
                            Feature faceDetection = new Feature();
                            faceDetection.setType("FACE_DETECTION");
                            faceDetection.setMaxResults(5);
                            add(faceDetection);
                            //*/
                        }});

                        // Add the list of four things to the request
                        add(annotateImageRequest);
                    }});

                    Vision.Images.Annotate annotateRequest =
                            vision.images().annotate(batchAnnotateImagesRequest);
                    // Due to a bug: requests to Vision API containing large images fail when GZipped.
                    annotateRequest.setDisableGZipContent(true);
                    Log.d("API:", "created Cloud Vision request object, sending request");

                    BatchAnnotateImagesResponse response = annotateRequest.execute();
                    return convertResponseToString(response);

                } catch (GoogleJsonResponseException e) {
                    Log.d("API:", "failed to make API request because " + e.getContent());
                } catch (IOException e) {
                    Log.d("API:", "failed to make API request because of other IOException " +
                            e.getMessage());
                }
                return "Cloud Vision API request failed. Check logs for details.";
            }

            protected void onPostExecute(String result) {
                textView.setText(result);
            }
        }.execute();
    }

    // Convert API Response to String
    private String convertResponseToString(BatchAnnotateImagesResponse response) {
        String message = "";
        ///* OCR
        List<EntityAnnotation> words = response.getResponses().get(0).getTextAnnotations();
        if (words != null) {
            message += "I found these words:\n";
            for (EntityAnnotation word : words) {
                message += String.format("%s", word.getDescription());
                message += "\n";
            }
        } else {
            message += "I found no words.";
        }
        message += "\n____________________________________________\n";
        //*/

        ///* LABEL
        List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();
        if (labels != null) {
            message += "I found these tags:\n";
            for (EntityAnnotation label : labels) {
                message += String.format("%s", label.getDescription());
                message += "\n";
            }
        } else {
            message += "I found no tags.";
        }
        message += "____________________________________________\n";
        //*/

        ///* LOGOS
        List<EntityAnnotation> logos = response.getResponses().get(0).getLogoAnnotations();
        if (logos != null) {
            message += "I found these logos:\n\n";
            for (EntityAnnotation logo : logos) {
                message += String.format("%s", logo.getDescription());
                message += "\n";
            }
        } else {
            message += "I found no logos.";
        }
        message += "\n____________________________________________\n";
        //*/

        ///* FACES
        List<FaceAnnotation> faces = response.getResponses().get(0).getFaceAnnotations();
        if (faces != null) {
            message += "I found these facial expressions:\n\n";
            for (FaceAnnotation face : faces) {
                message += String.format("Joy: %s\n", face.getJoyLikelihood());
                message += String.format("Sorrow: %s\n", face.getSorrowLikelihood());
                message += String.format("Anger: %s\n", face.getAngerLikelihood());
                message += String.format("Surprise: %s\n", face.getSurpriseLikelihood());
                message += "\n";
            }
        }
        else {
            message += "I found no faces.";
        }
        message += "\n____________________________________________\n";
        //*/

        return message;
    }
}