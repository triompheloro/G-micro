package com.example.projet;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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

import com.example.projet.api.OpenAIManager;
import com.example.projet.data.ConversationBuffer;
import com.example.projet.data.Message;
import com.example.projet.ml.ObjectDetector;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String OPENAI_API_KEY = "";  // Remplacez par votre clé API OpenAI



    private Button btnStart;
    private Button btnStop;
    private PreviewView viewFinder;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private ImageCapture imageCapture;
    private Handler mainHandler;
    private TextToSpeech tts;
    private ObjectDetector objectDetector;
    private OpenAIManager openAIManager;
    private ConversationBuffer conversationBuffer;
    private boolean isListening = false;
    private boolean allPermissionsGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            EdgeToEdge.enable(this);
            setContentView(R.layout.activity_main);

            setupViews();
            setupOpenAI();
            setupTTS();
            setupObjectDetector();
            checkPermissions();

            if (allPermissionsGranted) {
                startCamera();
                setupSpeechRecognizer();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'initialisation de MainActivity: " + e.getMessage(), e);
            Toast.makeText(this, "Erreur lors du démarrage: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupViews() {
        btnStart = findViewById(R.id.button_start);
        btnStop = findViewById(R.id.button_stop);
        viewFinder = findViewById(R.id.viewFinder);

        btnStart.setOnClickListener(v -> startListening());
        btnStop.setOnClickListener(v -> stopListening());
    }

    private void setupOpenAI() {
        openAIManager = new OpenAIManager(OPENAI_API_KEY);
        conversationBuffer = new ConversationBuffer();
    }

    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.FRANCE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Langue française non supportée");
                    // Essayons le français canadien comme alternative
                    result = tts.setLanguage(Locale.CANADA_FRENCH);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "Aucune langue française disponible");
                    }
                }
            } else {
                Log.e(TAG, "Initialisation TTS échouée");
            }
        });
    }

    private void setupObjectDetector() {
        mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            objectDetector = new ObjectDetector(this);
            Log.d(TAG, "Détecteur initialisé");
        });
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.INTERNET
        };

        List<String> permissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                permissionsNeeded.toArray(new String[0]),
                PERMISSION_REQUEST_CODE);
        } else {
            allPermissionsGranted = true;
        }
    }

    private void setupSpeechRecognizer() {
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        if (speechRecognizer != null) {
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
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // C'est la fonction qui verifie les permissions : du micro, du camera
        // ce sont les deux capteurs que nous avons utilisé pour le projet

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            allPermissionsGranted = allGranted;

            if (allGranted) {
                startCamera();
                setupSpeechRecognizer();
            } else {
                boolean shouldShowRationale = false;
                for (String permission : permissions) {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        shouldShowRationale = true;
                        break;
                    }
                }

                if (shouldShowRationale) {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Permissions requises")
                        .setMessage("Pour utiliser l'application, vous devez activer les permissions dans les paramètres de l'application.")
                        .setPositiveButton("Ouvrir les paramètres", (dialog, which) -> {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Quitter", (dialog, which) -> finish())
                        .setCancelable(false)
                        .show();
                } else {
                    // L'utilisateur a simplement refusé cette fois
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Permissions nécessaires")
                        .setMessage("Ces permissions sont nécessaires pour le fonctionnement de l'application. Voulez-vous réessayer ?")
                        .setPositiveButton("Réessayer", (dialog, which) -> checkPermissions())
                        .setNegativeButton("Quitter", (dialog, which) -> finish())
                        .setCancelable(false)
                        .show();
                }
            }
        }
    }

    private void startListening() {
        if (!allPermissionsGranted) {
            Toast.makeText(this, "Veuillez accorder toutes les permissions nécessaires", Toast.LENGTH_LONG).show();
            checkPermissions();
            return;
        }
        
        // Arrêter la synthèse vocale si elle est en cours
        if (tts != null) {
            tts.stop();
        }
        
        if (speechRecognizer != null) {
            speechRecognizer.startListening(recognizerIntent);
        }
    }

    private void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (objectDetector != null) {
            objectDetector.close();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    private void startCamera() {
        try {
            bindCameraUseCases();
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du démarrage de la caméra: " + e.getMessage(), e);
            Toast.makeText(this, "Erreur caméra: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void bindCameraUseCases() {
        try {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
            cameraProviderFuture.addListener(() -> {
                try {
                    ProcessCameraProvider provider = cameraProviderFuture.get();
                    Preview preview = new Preview.Builder()
                            .setTargetResolution(new Size(1280, 720))
                            .build();
                    preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                    imageCapture = new ImageCapture.Builder()
                            .setTargetResolution(new Size(1280, 720))
                            .build();

                    provider.unbindAll();
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
                    Log.d(TAG, "Caméra démarrée avec succès");
                } catch (Exception e) {
                    Log.e(TAG, "Erreur lors de la configuration de la caméra", e);
                    Toast.makeText(MainActivity.this, "Erreur caméra: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }, ContextCompat.getMainExecutor(this));
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du démarrage asynchrone de la caméra: " + e.getMessage(), e);
        }
    }

    private void captureFrameAndDetect() {
        if (imageCapture != null) {
            // Tableau de phrases d'introduction variées
            String[] startPhrases = {
                "D'accord, je vais analyser la scène.",
                "Un instant, j'analyse la scène.",
                "Je vais vous dire ce que j'aperçois."
            };
            // Sélection aléatoire d'une phrase
            String startPhrase = startPhrases[(int) (Math.random() * startPhrases.length)];
            tts.speak(startPhrase, TextToSpeech.QUEUE_FLUSH, null, null);

            imageCapture.takePicture(
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageCapturedCallback() {
                        @Override
                        public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                            analyzeSingleImage(imageProxy);
                        }
                        @Override
                        public void onError(@NonNull ImageCaptureException e) {
                            Log.e(TAG, "Erreur lors de la capture", e);
                            tts.speak("Désolé, je n'ai pas pu prendre la photo correctement.", 
                                    TextToSpeech.QUEUE_FLUSH, null, null);
                        }
                    });
        }
    }

    private void processUserInput(String userInput) {
        // Ici, pour une question d'optimisation de tokken nous avons decidé de lister
        //  les comande evident qui vont demander la detection d'objet,
        //  pour ne plus passer vers le  model LLM de OpenAi
        List<String> detectionCommands = Arrays.asList(
            "que vois-tu",
            "qu'est-ce que tu vois",
            "what do you see",
            "que vois tu",
            "qu'est ce que tu vois",
            "montre moi",
            "décris ce que tu vois",
            "décris moi",
            "décris-moi",
            "que peux-tu voir",
            "dis-moi ce que tu vois",
            "dis moi ce que tu vois",
            "vois-tu",
            "peux-tu me montrer",
            "analyse",
            "regarde",
            "observe"
        );
        String userInputLower = userInput.toLowerCase().trim();
        boolean isDirectDetectionCommand = detectionCommands.stream()
            .anyMatch(cmd -> userInputLower.contains(cmd.toLowerCase()));

        if (isDirectDetectionCommand) {
            runOnUiThread(() -> captureFrameAndDetect());
            return;
        }

        // Si ce n'est pas une commande directe, c'est là qu'on asse la commande vers le LLM
        Message userMessage = new Message("user", userInput);
        conversationBuffer.addMessage(userMessage);

        // Utiliser OpenAI directement pour une conversation naturelle
        openAIManager.generateResponse(userInput, conversationBuffer.getContextForAI(),
                new OpenAIManager.ResponseCallback() {
                    @Override
                    public void onResponseReady(String response) {
                        runOnUiThread(() -> {
                            String cleanResponse = response
                                .replaceAll("^[\"']*|[\"']*$", "") // Enlève les guillemets
                                .trim();
                            
                            Message assistantMessage = new Message("assistant", cleanResponse);
                            conversationBuffer.addMessage(assistantMessage);
                            tts.speak(cleanResponse, TextToSpeech.QUEUE_FLUSH, null, null);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Log.e(TAG, "Erreur OpenAI: " + error);
                            tts.speak("Désolé, je n'ai pas bien compris. Pouvez-vous répéter?", 
                                    TextToSpeech.QUEUE_FLUSH, null, null);
                        });
                    }
                });
    }

    private void analyzeSingleImage(final ImageProxy imageProxy) {
        if (objectDetector == null) {
            Toast.makeText(MainActivity.this, "Détecteur non initialisé", Toast.LENGTH_SHORT).show();
            imageProxy.close();
            return;
        }

        try {
            List<ObjectDetector.DetectionResult> detectionResults = objectDetector.detect(imageProxy);

            if (detectionResults.isEmpty()) {
                Toast.makeText(MainActivity.this, "Aucun objet détecté", Toast.LENGTH_LONG).show();
            } else {
                StringBuilder result = new StringBuilder();
                List<String> detectedLabels = new ArrayList<>();

                int count = Math.min(3, detectionResults.size());
                for (int i = 0; i < count; i++) {
                    ObjectDetector.DetectionResult detection = detectionResults.get(i);
                    result.append(detection.label)
                            .append(" (")
                            .append(String.format("%.0f%%", detection.confidence * 100))
                            .append(")");
                    if (i < count - 1) result.append(", ");

                    detectedLabels.add(detection.label);
                }

                Log.d(TAG, "Objets détectés: " + result);
                parlerDetections(detectedLabels);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'analyse", e);
            Toast.makeText(MainActivity.this, "Erreur: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            imageProxy.close();
        }
    }

    private void parlerDetections(List<String> labels) {
        // Cette fonction va servir de passer les label venant du tensorflow Lite
        // Le model va juste construire une phrase pour etre plus naturelle que au lieu d'avoir
        // une phrase statique à chaque fois

        if (labels == null || labels.isEmpty()) {
            tts.speak("Je ne vois rien de particulier pour le moment.", TextToSpeech.QUEUE_FLUSH, null, null);
            return;
        }

        // Construction de la liste des objets
        StringBuilder labelsList = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            labelsList.append(labels.get(i));
            if (i < labels.size() - 2) {
                labelsList.append(", ");
            } else if (i == labels.size() - 2) {
                labelsList.append(" et ");
            }
        }

        // Voici un prompt de pour dire au model formuler une phrase de réponse
        String prompt = "Tu vois les objets suivants : " + labelsList.toString() + 
                       ". Formule UNE SEULE phrase simple en français pour décrire UNIQUEMENT ce que tu vois. " +
                       "La phrase doit commencer par 'Je vois' ou 'J'aperçois'. " +
                       "Ne réponds que par cette phrase. Pas de ponctuation finale. Pas d'explication.";

        openAIManager.analyzeCommand(prompt, "", new OpenAIManager.CommandAnalysisCallback() {
            @Override
            public void onAnalysisComplete(String response, boolean isDetection) {
                runOnUiThread(() -> {
                    String cleanResponse = response
                        .replaceAll("^[\"']*|[\"']*$", "")
                        .replaceAll("Réponse\\s*:\\s*", "")
                        .replaceAll("(?i)détect.*?:", "")
                        .replaceAll("(?i)voici.*?:", "")
                        .replaceAll("\\s+", " ")
                        .trim();
                    
                    // Enlève la ponctuation finale si présente
                    if (cleanResponse.endsWith(".")) {
                        cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 1);
                    }
                    
                    // Si la réponse est vide ou trop courte, utiliser une réponse de base
                    if (cleanResponse.isEmpty() || cleanResponse.length() < 5) {
                        cleanResponse = "Je vois " + labelsList.toString();
                    }

                    Message assistantMessage = new Message("assistant", cleanResponse);
                    conversationBuffer.addMessage(assistantMessage);
                    tts.speak(cleanResponse, TextToSpeech.QUEUE_FLUSH, null, null);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    // Fallback en cas d'erreur
                    String fallbackText = "Je vois " + labelsList.toString();
                    tts.speak(fallbackText, TextToSpeech.QUEUE_FLUSH, null, null);
                });
            }
        });
    }
}