package com.aiassistant;

import android.service.voice.VoiceInteractionService;

/**
 * Registers Aria as a selectable "Digital assistant app". Once the user picks
 * Aria in Settings → Default apps → Digital assistant, long-pressing Home (or
 * the assist gesture) starts a session (see AssistSessionService).
 */
public class AssistInteractionService extends VoiceInteractionService {
}
