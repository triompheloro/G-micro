package com.example.projet;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

public class SplashScreenActivity extends AppCompatActivity {

    private static final String TAG = "SplashScreenActivity";
    private static final int SPLASH_DURATION = 2000; // 2 secondes

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_splash_screen);
            Log.d(TAG, "Splash screen layout loaded successfully");
            
            // Afficher le splash screen pendant 2 secondes, puis lancer MainActivity
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Log.d(TAG, "Transitioning to MainActivity");
                    Intent intent = new Intent(SplashScreenActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish(); // Fermer le splash screen
                } catch (Exception e) {
                    Log.e(TAG, "Error starting MainActivity: " + e.getMessage(), e);
                    finish();
                }
            }, SPLASH_DURATION);
        } catch (Exception e) {
            Log.e(TAG, "Error in SplashScreenActivity onCreate: " + e.getMessage(), e);
            // En cas d'erreur, lancer directement MainActivity
            Intent intent = new Intent(SplashScreenActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }
}
