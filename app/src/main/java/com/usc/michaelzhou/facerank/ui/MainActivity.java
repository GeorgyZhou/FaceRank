package com.usc.michaelzhou.facerank.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Emotion;
import com.microsoft.projectoxford.face.contract.Face;
import com.microsoft.projectoxford.face.contract.FacialHair;
import com.microsoft.projectoxford.face.contract.HeadPose;
import com.usc.michaelzhou.facerank.R;
import com.usc.michaelzhou.facerank.helper.FaceRankApp;
import com.usc.michaelzhou.facerank.helper.ImageHelper;
import com.usc.michaelzhou.facerank.helper.LogHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    // Flag to indicate which task is to be performed.
    private static final int REQUEST_SELECT_IMAGE = 0;

    private int FAIL_TIME = 0;

    // The URI of the image selected to detect.
    private Uri mImageUri;

    // The image selected to detect.
    private Bitmap mBitmap;

    private ListView listView;

    // Progress dialog popped up when communicating with server.
    ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle(getString(R.string.progress_dialog_title));

        listView = (ListView)findViewById(R.id.result_list_view);
        listView.setVisibility(View.INVISIBLE);
    }

    // Save the activity state when it's going to stop.
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable("ImageUri", mImageUri);
    }

    // Recover the saved state when the activity is recreated.
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mImageUri = savedInstanceState.getParcelable("ImageUri");
        if (mImageUri != null) {
            mBitmap = ImageHelper.loadSizeLimitedBitmapFromUri(
                    mImageUri, getContentResolver());
        }
    }

    public void upload(View view){
        Intent intent = new Intent(this, SelectImageActivity.class);
        startActivityForResult(intent, REQUEST_SELECT_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SELECT_IMAGE:
                if (resultCode == RESULT_OK) {
                    // If image is selected successfully, set the image URI and bitmap.
                    mImageUri = data.getData();
                    mBitmap = ImageHelper.loadSizeLimitedBitmapFromUri(
                            mImageUri, getContentResolver());
                    if (mBitmap != null) {
                        // Show the image on screen.
                        ImageView imageView = (ImageView) findViewById(R.id.welcome_image);
                        imageView.setImageBitmap(mBitmap);

                        imageView.setVisibility(View.VISIBLE);
                        listView.setVisibility(View.INVISIBLE);

                        // Add detection log.
                        addLog("Image: " + mImageUri + " resized to " + mBitmap.getWidth()
                                + "x" + mBitmap.getHeight());

                        setButtonRank(true);
                    }
                    else{
                        setButtonUpload(true);
                    }
                }
                break;
            default:
                break;
        }
    }


    // The adapter of the GridView which contains the details of the detected faces.
    private class FaceListAdapter extends BaseAdapter {
        // The detected faces.
        List<Face> faces;

        // The thumbnails of detected faces.
        List<Bitmap> faceThumbnails;

        // Initialize with detection result.
        FaceListAdapter(Face[] detectionResult) {
            faces = new ArrayList<>();
            faceThumbnails = new ArrayList<>();

            if (detectionResult != null) {
                faces = Arrays.asList(detectionResult);
                for (Face face : faces) {
                    try {
                        // Crop face thumbnail with five main landmarks drawn from original image.
                        faceThumbnails.add(ImageHelper.generateFaceThumbnail(
                                mBitmap, face.faceRectangle));
                    } catch (IOException e) {
                        // Show the exception when generating face thumbnail fails.
                        Log.e("RESULT_ACTIVITY", e.getMessage());
                    }
                }
            }
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public int getCount() {
            return faces.size();
        }

        @Override
        public Object getItem(int position) {
            return faces.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater layoutInflater =
                        (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = layoutInflater.inflate(R.layout.item_face_with_description, parent, false);
            }
            convertView.setId(position);

            // Show the face thumbnail.
            ((ImageView) convertView.findViewById(R.id.face_thumbnail)).setImageBitmap(
                    faceThumbnails.get(position));

            double faceAge = (double)faces.get(position).faceAttributes.age;
            String faceGender = faces.get(position).faceAttributes.gender;
            double smile = (double)faces.get(position).faceAttributes.smile;

            double score = (faceGender == "female" ? 1.0 : 0.0) + smile * 10.0 + 100.0 - faceAge;

            // Show the face details.
            DecimalFormat formatter = new DecimalFormat("#0.0");
            String face_description = String.format("Age: %s\nGender: %s\nSmile: %s\nGlasses: %s\nFacialHair: %s\nEmotion:%s\nScore:%s\n",
                    faces.get(position).faceAttributes.age,
                    faces.get(position).faceAttributes.gender,
                    faces.get(position).faceAttributes.smile,
                    faces.get(position).faceAttributes.glasses,
                    getFacialHair(faces.get(position).faceAttributes.facialHair),
                    getEmotion(faces.get(position).faceAttributes.emotion),
                    //getHeadPose(faces.get(position).faceAttributes.headPose),
                    score
            );
            ((TextView) convertView.findViewById(R.id.text_detected_face)).setText(face_description);

            return convertView;
        }

        private String getFacialHair(FacialHair facialHair)
        {
            return (facialHair.moustache + facialHair.beard + facialHair.sideburns > 0) ? "Yes" : "No";
        }

        private String getEmotion(Emotion emotion)
        {
            String emotionType = "";
            double emotionValue = 0.0;
            if (emotion.anger > emotionValue)
            {
                emotionValue = emotion.anger;
                emotionType = "Anger";
            }
            if (emotion.contempt > emotionValue)
            {
                emotionValue = emotion.contempt;
                emotionType = "Contempt";
            }
            if (emotion.disgust > emotionValue)
            {
                emotionValue = emotion.disgust;
                emotionType = "Disgust";
            }
            if (emotion.fear > emotionValue)
            {
                emotionValue = emotion.fear;
                emotionType = "Fear";
            }
            if (emotion.happiness > emotionValue)
            {
                emotionValue = emotion.happiness;
                emotionType = "Happiness";
            }
            if (emotion.neutral > emotionValue)
            {
                emotionValue = emotion.neutral;
                emotionType = "Neutral";
            }
            if (emotion.sadness > emotionValue)
            {
                emotionValue = emotion.sadness;
                emotionType = "Sadness";
            }
            if (emotion.surprise > emotionValue)
            {
                emotionValue = emotion.surprise;
                emotionType = "Surprise";
            }
            return String.format("%s: %f", emotionType, emotionValue);
        }

        private String getHeadPose(HeadPose headPose)
        {
            return String.format("Pitch: %s, Roll: %s, Yaw: %s", headPose.pitch, headPose.roll, headPose.yaw);
        }
    }



    // Set whether the buttons are enabled.
    private void setAllButtonsEnabledStatus(boolean isEnabled) {
        Button detectButton = (Button) findViewById(R.id.photo_upload_button);
        detectButton.setEnabled(isEnabled);
    }

    private void detect(View view){
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        // Start a background task to detect faces in the image.
        new DetectionTask().execute(inputStream);

        // Prevent button click during detecting.
        setAllButtonsEnabledStatus(false);
    }

    // Background task of face detection.
    private class DetectionTask extends AsyncTask<InputStream, String, Face[]> {
        private boolean mSucceed = true;

        @Override
        protected Face[] doInBackground(InputStream... params) {
            // Get an instance of face service client to detect faces in image.
            FaceServiceClient faceServiceClient = new FaceServiceRestClient(getString(R.string.subscription_key));
            try {
                publishProgress("Detecting...");

                // Start detection.
                return faceServiceClient.detect(
                        params[0],  /* Input stream of image to detect */
                        true,       /* Whether to return face ID */
                        true,       /* Whether to return face landmarks */
                        /* Which face attributes to analyze, currently we support:
                           age,gender,headPose,smile,facialHair */
                        new FaceServiceClient.FaceAttributeType[]{
                                FaceServiceClient.FaceAttributeType.Age,
                                FaceServiceClient.FaceAttributeType.Gender,
                                FaceServiceClient.FaceAttributeType.Smile,
                                FaceServiceClient.FaceAttributeType.Glasses,
                                FaceServiceClient.FaceAttributeType.FacialHair,
                                FaceServiceClient.FaceAttributeType.Emotion,
                                FaceServiceClient.FaceAttributeType.HeadPose
                        });
            } catch (Exception e) {
                mSucceed = false;
                publishProgress(e.getMessage());
                addLog(e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            mProgressDialog.show();
            addLog("Request: Detecting in image " + mImageUri);
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            mProgressDialog.setMessage(progress[0]);
        }

        @Override
        protected void onPostExecute(Face[] result) {
            if (mSucceed) {
                Log.i("MainActivity", "Response: Success. Detected " + (result == null ? 0 : result.length)
                        + " face(s) in " + mImageUri);
            }

            // Show the result on screen when detection is done.
            setUiAfterDetection(result, mSucceed);
        }

        private void setUiAfterDetection(Face[] result, boolean succeed){
            mProgressDialog.dismiss();

            setAllButtonsEnabledStatus(true);

            if (succeed){
                if (result != null){
                    setButtonUpload(true);
                    ImageView imageView = (ImageView) findViewById(R.id.welcome_image);
                    imageView.setImageDrawable(getDrawable(R.drawable.welcome_image));
                    imageView.setVisibility(View.INVISIBLE);

                    FaceListAdapter faceListAdapter = new FaceListAdapter(result);

                    listView.setVisibility(View.VISIBLE);
                    listView.setAdapter(faceListAdapter);
                }
                else{
                    if (FAIL_TIME < 1){
                        FAIL_TIME++;
                        setButtonFail(true);
                    }
                    else{
                        FAIL_TIME = 0;
                        setButtonTryAnother(true);
                    }
                }
            }
            else{
                if(FAIL_TIME < 1){
                    FAIL_TIME++;
                    setButtonFail(true);
                }
                else{
                    FAIL_TIME = 0;
                    setButtonTryAnother(true);
                }
            }
        }
    }

    private void updateUI(Face[] result){
        ImageView imageView = (ImageView)findViewById(R.id.welcome_image);
        Button button = (Button) findViewById(R.id.photo_upload_button);
    }

    private void setButtonFail(boolean isEnabled){
        Button button = (Button)findViewById(R.id.photo_upload_button);
        button.setText(R.string.fail_to_detect);
        button.setEnabled(isEnabled);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                detect(v);
            }
        });
    }

    private void setButtonTryAnother(boolean isEnabled){
        Button button = (Button)findViewById(R.id.photo_upload_button);
        button.setText(R.string.try_another);
        button.setEnabled(isEnabled);
        button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                upload(v);
            }
        });
    }

    private void setButtonUpload(boolean isEnabled){
        Button button = (Button)findViewById(R.id.photo_upload_button);
        button.setText(R.string.upload_your_photo);
        button.setEnabled(isEnabled);
        button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                upload(v);
            }
        });
    }

    private void setButtonRank(Boolean isEnabled){
        Button button = (Button) findViewById(R.id.photo_upload_button);
        button.setText(R.string.begin_rank);
        button.setEnabled(isEnabled);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                detect(v);
            }
        });
    }

    // Add a log item.
    private void addLog(String log) {
        LogHelper.addDetectionLog(log);
    }
}
