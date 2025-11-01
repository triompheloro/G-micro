package com.example.projet.api;

import com.example.projet.api.models.OpenAIRequest;
import com.example.projet.api.models.OpenAIResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface OpenAIService {
    @Headers({
        "Content-Type: application/json"
    })
    @POST("v1/chat/completions")
    Call<OpenAIResponse> createChatCompletion(@Body OpenAIRequest request);
}