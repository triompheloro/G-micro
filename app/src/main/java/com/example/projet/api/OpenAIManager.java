package com.example.projet.api;

import android.util.Log;
import com.example.projet.api.models.OpenAIRequest;
import com.example.projet.api.models.OpenAIResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OpenAIManager {
    private static final String BASE_URL = "https://api.openai.com/";
    private static final String TAG = "OpenAIManager";
    
    private final OpenAIService service;
    private final String apiKey;

    public OpenAIManager(String apiKey) {
        this.apiKey = apiKey;
        
        // Configuration du client HTTP
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("Authorization", "Bearer " + apiKey)
                            .method(original.method(), original.body())
                            .build();
                    return chain.proceed(request);
                });

        // Ajout du logging en debug
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        clientBuilder.addInterceptor(logging);

        // Construction de Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(clientBuilder.build())
                .build();

        service = retrofit.create(OpenAIService.class);
    }

    public void analyzeCommand(String userInput, String context, CommandAnalysisCallback callback) {
        List<OpenAIRequest.Message> messages = new ArrayList<>();
        
        // Ajouter le système prompt pour expliquer le rôle
        messages.add(new OpenAIRequest.Message("system", 
            "Tu es un assistant qui analyse les commandes vocales. " +
            "Si la commande concerne la détection ou l'identification d'objets visibles (comme 'que vois-tu', 'qu'est-ce que c'est', etc.), " +
            "réponds avec 'DETECTION:' suivi d'une explication. Sinon, réponds normalement à la question."));
        
        // Ajouter le contexte si disponible
        if (context != null && !context.isEmpty()) {
            messages.add(new OpenAIRequest.Message("system", "Contexte précédent: " + context));
        }
        
        // Ajouter la commande de l'utilisateur
        messages.add(new OpenAIRequest.Message("user", userInput));

        OpenAIRequest request = new OpenAIRequest(messages);

        service.createChatCompletion(request).enqueue(new retrofit2.Callback<OpenAIResponse>() {
            @Override
            public void onResponse(retrofit2.Call<OpenAIResponse> call, retrofit2.Response<OpenAIResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String aiResponse = response.body().getFirstResponse();
                    if (aiResponse != null) {
                        boolean isDetection = aiResponse.startsWith("DETECTION:");
                        callback.onAnalysisComplete(aiResponse, isDetection);
                    } else {
                        callback.onError("Réponse vide de l'API");
                    }
                } else {
                    callback.onError("Erreur: " + response.code());
                }
            }

            @Override
            public void onFailure(retrofit2.Call<OpenAIResponse> call, Throwable t) {
                Log.e(TAG, "Erreur API", t);
                callback.onError("Erreur de connexion: " + t.getMessage());
            }
        });
    }

    public interface CommandAnalysisCallback {
        void onAnalysisComplete(String response, boolean isDetection);
        void onError(String error);
    }
}