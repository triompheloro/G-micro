package com.example.projet;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.Toast;

import com.example.projet.api.OpenAIManager;
import com.example.projet.data.ConversationBuffer;
import com.example.projet.data.Message;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String OPENAI_API_KEY = ""; // Remplacez par votre clé API

    private Button btnStart, btnStop;
    private PreviewView viewFinder;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private ImageCapture imageCapture;

    // Utilisez ImageLabeler au lieu d'ObjectDetector
    private ImageLabeler labeler;
    private Handler mainHandler;
    private TextToSpeech tts;

    // Nouvelles variables pour OpenAI et la gestion des conversations
    private OpenAIManager openAIManager;
    private ConversationBuffer conversationBuffer;
    private boolean isListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Initialisation de OpenAI et du buffer de conversation
        openAIManager = new OpenAIManager(OPENAI_API_KEY);
        conversationBuffer = new ConversationBuffer();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.FRANCE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Langue non supportée");
                }
            } else {
                Log.e("TTS", "Initialisation échouée");
            }
        });

        // Créez un Handler pour le thread principal
        mainHandler = new Handler(Looper.getMainLooper());

        // Initialisez l'Image Labeler pour avoir des labels précis
        mainHandler.post(() -> {
            ImageLabelerOptions options = new ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(0.5f)  // Seuil de confiance minimum (50%)
                    .build();

            labeler = ImageLabeling.getClient(options);
            Log.d("ImageLabeling", "Labeler initialisé");
        });

        // configuration UI
        btnStart = findViewById(R.id.button_start);
        btnStop = findViewById(R.id.button_stop);
        viewFinder = findViewById(R.id.viewFinder);

        final List<String> commandesAutorises = new ArrayList<>(List.of("what is this","what is that","what do you see"));

        // Vérification permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkAudioPermission();
            checkCameraPermission();
        }

        // Démarre la caméra
        startCamera();

        // Config SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                isListening = true;
                Toast.makeText(MainActivity.this, "Prêt à écouter", LENGTH_SHORT).show();
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {
                isListening = false;
            }
            @Override public void onError(int error) {
                isListening = false;
                Toast.makeText(MainActivity.this, "Veuillez répéter", LENGTH_LONG).show();
            }
            @Override public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    processUserInput(recognizedText);
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        btnStart.setOnClickListener(v -> startListening());
        btnStop.setOnClickListener(v -> stopListening());
    }

    private void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }
    }

    private void startListening() {
        speechRecognizer.startListening(recognizerIntent);
    }

    private void stopListening() {
        speechRecognizer.stopListening();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (labeler != null) {
            try {
                labeler.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    private void startCamera() {
        bindCameraUseCases();
    }

    private void bindCameraUseCases() {
        try {
            ProcessCameraProvider provider = ProcessCameraProvider.getInstance(this).get();
            Preview preview = new Preview.Builder()
                    .setTargetResolution(new Size(1280,720))
                    .build();
            preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

            imageCapture = new ImageCapture.Builder()
                    .setTargetResolution(new Size(1280,720))
                    .build();

            provider.unbindAll();
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void captureFrameAndDetect() {
        Toast.makeText(MainActivity.this, "Analyse de l'image...", Toast.LENGTH_SHORT).show();
        if (imageCapture != null) {
            imageCapture.takePicture(
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageCapturedCallback() {
                        @Override
                        public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                            Toast.makeText(MainActivity.this, "Capture réussie", Toast.LENGTH_SHORT).show();
                            analyzeSingleImage(imageProxy);
                        }
                        @Override
                        public void onError(@NonNull ImageCaptureException e) {
                            e.printStackTrace();
                            Toast.makeText(MainActivity.this, "Erreur capture", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void parlerLabels(List<ImageLabel> labels) {
        if (labels == null || labels.isEmpty()) return;

        StringBuilder texte = new StringBuilder(" I see : ");
        int nbLabels = Math.min(3, labels.size()); // limite à 3 objets pour plus de clarté

        for (int i = 0; i < nbLabels; i++) {
            texte.append(labels.get(i).getText());
            if (i < nbLabels - 1) {
                texte.append(", ");
            }
        }
        // Ajouter la réponse au buffer de conversation
        Message assistantMessage = new Message("assistant", texte.toString());
        conversationBuffer.addMessage(assistantMessage);
        
        // Utilise TTS pour parler
        tts.speak(texte.toString(), TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private void processUserInput(String userInput) {
        // Ajouter l'entrée utilisateur au buffer
        Message userMessage = new Message("user", userInput);
        conversationBuffer.addMessage(userMessage);

        // Analyser la commande avec OpenAI
        openAIManager.analyzeCommand(userInput, conversationBuffer.getContextForAI(), 
            new OpenAIManager.CommandAnalysisCallback() {
                @Override
                public void onAnalysisComplete(String response, boolean isDetection) {
                    runOnUiThread(() -> {
                        if (isDetection) {
                            // Si c'est une demande de détection, activer ML Kit
                            captureFrameAndDetect();
                        } else {
                            // Sinon, c'est une conversation normale
                            Message assistantMessage = new Message("assistant", response);
                            conversationBuffer.addMessage(assistantMessage);
                            
                            // Répondre vocalement
                            tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, null);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Erreur: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
    }

    private void analyzeSingleImage(final ImageProxy imageProxy) {
        if (labeler == null) {
            Toast.makeText(MainActivity.this, "Labeler non initialisé", Toast.LENGTH_SHORT).show();
            imageProxy.close();
            return;
        }

        try {
            if (imageProxy.getImage() == null) {
                Toast.makeText(MainActivity.this, "Image invalide", Toast.LENGTH_SHORT).show();
                imageProxy.close();
                return;
            }

            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            labeler.process(image)
                    .addOnSuccessListener(labels -> {
                        if (labels.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Aucun objet détecté", Toast.LENGTH_LONG).show();
                        } else {
                            StringBuilder result = new StringBuilder();

                            // Prenez les 3 premiers labels les plus probables
                            int count = Math.min(3, labels.size());
                            for (int i = 0; i < count; i++) {
                                ImageLabel label = labels.get(i);
                                result.append(label.getText())
                                        .append(" (")
                                        .append(String.format("%.0f%%", label.getConfidence() * 100))
                                        .append(")");
                                if (i < count - 1) result.append(", ");
                            }

                            Log.d("ImageLabeling", "Labels détectés: " + result.toString());
//                            Toast.makeText(MainActivity.this, "Je vois: " + result, Toast.LENGTH_LONG).show();
                            parlerLabels(labels);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("ImageLabeling", "Erreur de détection", e);
                        Toast.makeText(MainActivity.this, "Erreur: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        } catch (Exception e) {
            Log.e("ImageLabeling", "Erreur lors de l'analyse", e);
            imageProxy.close();
            Toast.makeText(MainActivity.this, "Erreur: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}