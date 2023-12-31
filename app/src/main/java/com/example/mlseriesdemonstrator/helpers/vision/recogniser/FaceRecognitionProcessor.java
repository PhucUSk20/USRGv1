package com.example.mlseriesdemonstrator.helpers.vision.recogniser;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.text.Editable;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import com.example.mlseriesdemonstrator.SQLConnection;
import com.example.mlseriesdemonstrator.helpers.vision.FaceGraphic;
import com.example.mlseriesdemonstrator.helpers.vision.GraphicOverlay;
import com.example.mlseriesdemonstrator.helpers.vision.VisionBaseProcessor;
import com.example.mlseriesdemonstrator.object.FaceRecognitionActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FaceRecognitionProcessor extends VisionBaseProcessor<List<Face>> {

    class Person {
        public String name;
        public float[] faceVector;

        public Person(String name, float[] faceVector) {
            this.name = name;
            this.faceVector = faceVector;
        }
    }

    public interface FaceRecognitionCallback {
        void onFaceRecognised(Face face, float probability, String name);
        void onFaceDetected(Face face, Bitmap faceBitmap, float[] vector);
    }

    private static final String TAG = "FaceRecognitionProcessor";

    // Input image size for our facenet model
    private static final int FACENET_INPUT_IMAGE_SIZE = 112;

    private final FaceDetector detector;
    private final Interpreter faceNetModelInterpreter;
    private final ImageProcessor faceNetImageProcessor;
    private final GraphicOverlay graphicOverlay;
    private final FaceRecognitionCallback callback;
    private final Connection sqlConnection;  // Kết nối SQL Server

    public FaceRecognitionActivity activity;

    List<Person> recognisedFaceList = new ArrayList();

    public FaceRecognitionProcessor(Interpreter faceNetModelInterpreter,
                                    GraphicOverlay graphicOverlay,
                                    FaceRecognitionCallback callback) {
        this.callback = callback;
        this.graphicOverlay = graphicOverlay;
        // Initialize processors
        this.faceNetModelInterpreter = faceNetModelInterpreter;
        faceNetImageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(FACENET_INPUT_IMAGE_SIZE, FACENET_INPUT_IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(0f, 255f))
                .build();

        FaceDetectorOptions faceDetectorOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .enableTracking()
                .build();
        detector = FaceDetection.getClient(faceDetectorOptions);

        // Initialize SQL connection
        sqlConnection = SQLConnection.getConnection();

        // Load recognised faces from the database
        loadRecognisedFacesFromDatabase();
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    public Task<List<Face>> detectInImage(ImageProxy imageProxy, Bitmap bitmap, int rotationDegrees) {
        InputImage inputImage = InputImage.fromMediaImage(imageProxy.getImage(), rotationDegrees);
        int rotation = rotationDegrees;

        boolean reverseDimens = rotation == 90 || rotation == 270;
        int width;
        int height;
        if (reverseDimens) {
            width = imageProxy.getHeight();
            height =  imageProxy.getWidth();
        } else {
            width = imageProxy.getWidth();
            height = imageProxy.getHeight();
        }
        return detector.process(inputImage)
                .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                    @Override
                    public void onSuccess(List<Face> faces) {
                        graphicOverlay.clear();
                        for (Face face : faces) {
                            FaceGraphic faceGraphic = new FaceGraphic(graphicOverlay, face, false, width, height);
                            Log.d(TAG, "face found, id: " + face.getTrackingId());

                            Bitmap faceBitmap = cropToBBox(bitmap, face.getBoundingBox(), rotation);

                            if (faceBitmap == null) {
                                Log.d("GraphicOverlay", "Face bitmap null");
                                return;
                            }

                            TensorImage tensorImage = TensorImage.fromBitmap(faceBitmap);
                            ByteBuffer faceNetByteBuffer = faceNetImageProcessor.process(tensorImage).getBuffer();
                            float[][] faceOutputArray = new float[1][192];
                            faceNetModelInterpreter.run(faceNetByteBuffer, faceOutputArray);

                            Log.d(TAG, "output array: " + Arrays.deepToString(faceOutputArray));

                            if (callback != null) {
                                callback.onFaceDetected(face, faceBitmap, faceOutputArray[0]);
                                if (!recognisedFaceList.isEmpty()) {
                                    Pair<String, Float> result = findNearestFace(faceOutputArray[0]);
                                    if (result.second < 1.0f) {
                                        faceGraphic.name = result.first;
                                        callback.onFaceRecognised(face, result.second, result.first);
                                    }
                                }
                            }

                            graphicOverlay.add(faceGraphic);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Intentionally left empty
                    }
                });
    }

    private Pair<String, Float> findNearestFace(float[] vector) {
        Pair<String, Float> ret = null;
        for (Person person : recognisedFaceList) {
            final String name = person.name;
            final float[] knownVector = person.faceVector;

            float distance = 0;
            for (int i = 0; i < vector.length; i++) {
                float diff = vector[i] - knownVector[i];
                distance += diff * diff;
            }
            distance = (float) Math.sqrt(distance);
            if (ret == null || distance < ret.second) {
                ret = new Pair<>(name, distance);
            }
        }
        return ret;
    }

    public void stop() {
        detector.close();
    }

    private Bitmap cropToBBox(Bitmap image, Rect boundingBox, int rotation) {
        int shift = 0;
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            image = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true);
        }
        if (boundingBox.top >= 0 && boundingBox.bottom <= image.getWidth()
                && boundingBox.top + boundingBox.height() <= image.getHeight()
                && boundingBox.left >= 0
                && boundingBox.left + boundingBox.width() <= image.getWidth()) {
            return Bitmap.createBitmap(
                    image,
                    boundingBox.left,
                    boundingBox.top + shift,
                    boundingBox.width(),
                    boundingBox.height()
            );
        } else return null;
    }

    public void registerFace(Editable input, float[] tempVector, byte[] imageBytes) {
        recognisedFaceList.add(new Person(input.toString(), tempVector));
        savePersonToDatabase(input.toString(), tempVector, imageBytes);
    }

    private void savePersonToDatabase(String name, float[] faceVector, byte[] imageBytes) {
        if (sqlConnection != null) {
            try {
                String insertQuery = "INSERT INTO Person (Name, FaceVector, ImageData) VALUES (?, ?, ?)";
                PreparedStatement preparedStatement = sqlConnection.prepareStatement(insertQuery);
                preparedStatement.setString(1, name);
                byte[] faceVectorBytes = convertFloatArrayToBytes(faceVector);
                preparedStatement.setBytes(2, faceVectorBytes);
                preparedStatement.setBytes(3, imageBytes); // Lưu trữ hình ảnh dưới dạng binary data
                preparedStatement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private byte[] convertImageToBytes(Bitmap image) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }


    private byte[] convertFloatArrayToBytes(float[] floatArray) {
        ByteBuffer buffer = ByteBuffer.allocate(floatArray.length * 4);
        for (float value : floatArray) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    private void loadRecognisedFacesFromDatabase() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (sqlConnection != null) {
                    try {
                        String selectQuery = "SELECT Name, FaceVector FROM Person";
                        PreparedStatement preparedStatement = sqlConnection.prepareStatement(selectQuery);
                        ResultSet resultSet = preparedStatement.executeQuery();

                        while (resultSet.next()) {
                            String name = resultSet.getString("Name");
                            byte[] faceVectorBytes = resultSet.getBytes("FaceVector");
                            float[] faceVector = convertBytesToFloatArray(faceVectorBytes);
                            recognisedFaceList.add(new Person(name, faceVector));
                        }

                        resultSet.close();
                        preparedStatement.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        thread.start();
    }


    private float[] convertBytesToFloatArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] floatArray = new float[bytes.length / 4];
        for (int i = 0; i < floatArray.length; i++) {
            floatArray[i] = buffer.getFloat();
        }
        return floatArray;
    }
}
