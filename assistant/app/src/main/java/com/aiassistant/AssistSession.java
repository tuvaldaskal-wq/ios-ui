package com.aiassistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;

/** When invoked (long-press Home), open Aria and start listening immediately. */
public class AssistSession extends VoiceInteractionSession {

    public AssistSession(Context context) {
        super(context);
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        Intent i = new Intent(getContext(), VoiceActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra(VoiceActivity.EXTRA_FROM_ASSIST, true);
        getContext().startActivity(i);
        hide();
    }
}
