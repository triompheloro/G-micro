package com.example.projet.ml;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.util.Log;
import androidx.camera.core.ImageProxy;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;

import java.nio.ByteBuffer;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ObjectDetector {
    private static final String TAG = "ObjectDetector";
    private static final String MODEL_NAME = "mobilenet_v2.tflite";
    private static final int IMAGE_SIZE = 224;  // MobileNetV2 input size
    private static final float SCORE_THRESHOLD = 0.3f;  // Minimum confidence score

    private final Context context;
    private ImageClassifier classifier;
    private final ImageProcessor imageProcessor;

    public static class DetectionResult {
        public final String label;
        public final float confidence;

        public DetectionResult(String label, float confidence) {
            this.label = label;
            this.confidence = confidence;
        }
    }

    public ObjectDetector(Context context) {
        this.context = context;
        this.imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(IMAGE_SIZE, IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .build();
        setupClassifier();
    }

    private void setupClassifier() {
        try {
            BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder()
                    .setNumThreads(4)
                    .useNnapi();  // Utilise le Neural Network API pour de meilleures performances

            ImageClassifier.ImageClassifierOptions options = ImageClassifier.ImageClassifierOptions.builder()
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setMaxResults(5)
                    .setScoreThreshold(SCORE_THRESHOLD)
                    .setDisplayNamesLocale("fr")  // Pour avoir les labels en français si disponible
                    .build();

            classifier = ImageClassifier.createFromFileAndOptions(
                    context,
                    MODEL_NAME,
                    options);

        } catch (IOException e) {
            Log.e(TAG, "Error loading model", e);
        }
    }

    public List<DetectionResult> detect(ImageProxy imageProxy) {
        if (classifier == null) {
            Log.e(TAG, "Classifier not initialized");
            return new ArrayList<>();
        }

        try {
            @SuppressLint("UnsafeOptInUsageError")
            Image image = imageProxy.getImage();
            if (image == null) return new ArrayList<>();

            TensorImage tensorImage = new TensorImage(DataType.UINT8);
            Bitmap bitmap = imageProxy.toBitmap();
            tensorImage.load(bitmap);
            tensorImage = imageProcessor.process(tensorImage);

            List<Classifications> results = classifier.classify(tensorImage);
            List<DetectionResult> detectionResults = new ArrayList<>();

            if (!results.isEmpty() && results.get(0).getCategories() != null) {
                results.get(0).getCategories().forEach(category -> 
                    detectionResults.add(new DetectionResult(
                        category.getLabel(),
                        category.getScore()
                    ))
                );
            }

            return detectionResults;

        } catch (Exception e) {
            Log.e(TAG, "Error running detection", e);
            return new ArrayList<>();
        }
    }

    public void close() {
        if (classifier != null) {
            classifier.close();
            classifier = null;
        }
    }
}