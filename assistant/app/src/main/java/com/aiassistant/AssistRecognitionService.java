package com.aiassistant;

import android.content.Intent;
import android.speech.RecognitionService;

/**
 * Stub recognition service. A digital-assistant app must declare one; Aria does
 * its own listening inside VoiceActivity, so this is intentionally empty.
 */
public class AssistRecognitionService extends RecognitionService {

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
    }

    @Override
    protected void onCancel(Callback listener) {
    }

    @Override
    protected void onStopListening(Callback listener) {
    }
}
