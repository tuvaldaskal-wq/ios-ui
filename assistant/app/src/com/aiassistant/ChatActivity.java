package com.aiassistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

/** iOS-style chat screen for the Aria assistant. */
public class ChatActivity extends Activity {

    private static final int REQ_VOICE = 100;
    private static final int REQ_PERMS = 101;

    private Config config;
    private Agent agent;
    private TextToSpeech tts;
    private boolean ttsReady;

    private ScrollView scroll;
    private LinearLayout messages;
    private EditText input;
    private View statusRow;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        config = new Config(this);
        agent = new Agent(this, config);
        setContentView(buildUi());

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ttsReady = status == TextToSpeech.SUCCESS;
                if (ttsReady) {
                    tts.setLanguage(Locale.getDefault());
                }
            }
        });

        requestPerms();

        if (!config.isConfigured()) {
            addBubble(Message.ASSISTANT,
                    "👋 Hi, I'm Aria. To start, add your Anthropic API key in "
                    + "app/res/values/secrets.xml and rebuild. Then ask me to open apps, "
                    + "text someone, set a timer, and more.");
        } else {
            addBubble(Message.ASSISTANT,
                    "👋 Hi, I'm Aria. Ask me anything, or tell me to do something — "
                    + "\"open Brawl Stars\", \"text Mom I'm on my way\", \"set a 10 minute timer\".");
        }
    }

    private void requestPerms() {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            return;
        }
        try {
            requestPermissions(new String[]{
                    android.Manifest.permission.SEND_SMS,
                    android.Manifest.permission.CALL_PHONE,
                    android.Manifest.permission.READ_CONTACTS,
                    android.Manifest.permission.RECORD_AUDIO
            }, REQ_PERMS);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);

        // Header
        TextView header = new TextView(this);
        header.setText("Aria");
        header.setTextColor(Color.WHITE);
        header.setTextSize(20f);
        header.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, dp(14), 0, dp(12));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Messages
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(12), dp(6), dp(12), dp(6));
        scroll.addView(messages);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Input bar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(8), dp(10), dp(12));

        input = new EditText(this);
        input.setHint("Message Aria…");
        input.setHintTextColor(Color.parseColor("#80FFFFFF"));
        input.setTextColor(Color.WHITE);
        input.setBackgroundResource(R.drawable.input_bg);
        input.setPadding(dp(16), dp(11), dp(16), dp(11));
        input.setMaxLines(4);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent e) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendCurrent();
                    return true;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(input, inLp);

        TextView mic = circleButton("🎤", "#33FFFFFF");
        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startVoice();
            }
        });
        bar.addView(mic, marginStart(dp(8)));

        TextView send = circleButton("↑", "#0A84FF");
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCurrent();
            }
        });
        bar.addView(send, marginStart(dp(8)));

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private TextView circleButton(String glyph, String color) {
        TextView t = new TextView(this);
        t.setText(glyph);
        t.setTextColor(Color.WHITE);
        t.setTextSize(18f);
        t.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(Color.parseColor(color));
        t.setBackground(bg);
        int s = dp(44);
        t.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return t;
    }

    private LinearLayout.LayoutParams marginStart(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.leftMargin = px;
        return lp;
    }

    private TextView addBubble(int type, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(4);
        rowLp.bottomMargin = dp(4);
        row.setLayoutParams(rowLp);

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(16f);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.82));

        if (type == Message.USER) {
            bubble.setTextColor(Color.WHITE);
            bubble.setBackgroundResource(R.drawable.bubble_user);
            row.setGravity(Gravity.END);
        } else if (type == Message.ASSISTANT) {
            bubble.setTextColor(Color.WHITE);
            bubble.setBackgroundResource(R.drawable.bubble_assistant);
            row.setGravity(Gravity.START);
        } else {
            bubble.setTextColor(Color.parseColor("#99FFFFFF"));
            bubble.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
            row.setGravity(Gravity.START);
        }
        row.addView(bubble);
        messages.addView(row);
        scrollDown();
        if (type == Message.STATUS) {
            statusRow = row;
            statusText = bubble;
        }
        return bubble;
    }

    private void scrollDown() {
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void clearStatus() {
        if (statusRow != null) {
            messages.removeView(statusRow);
            statusRow = null;
            statusText = null;
        }
    }

    // ------------------------------------------------------------------
    // Send / receive
    // ------------------------------------------------------------------

    private void sendCurrent() {
        String text = input.getText().toString().trim();
        if (text.length() == 0) {
            return;
        }
        input.setText("");
        addBubble(Message.USER, text);

        if (!config.isConfigured()) {
            addBubble(Message.ASSISTANT,
                    "I need an API key first — add it to secrets.xml and rebuild.");
            return;
        }

        addBubble(Message.STATUS, "Thinking…");
        agent.send(text, new Agent.Listener() {
            @Override
            public void onStatus(String note) {
                if (statusText != null) {
                    statusText.setText(note);
                    scrollDown();
                }
            }
            @Override
            public void onResult(String reply) {
                clearStatus();
                if (reply != null && reply.length() > 0) {
                    addBubble(Message.ASSISTANT, reply);
                    speak(reply);
                }
            }
            @Override
            public void onError(String message) {
                clearStatus();
                addBubble(Message.ASSISTANT, "⚠ " + message);
            }
        });
    }

    private void speak(String text) {
        if (ttsReady && text.length() < 600) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aria");
        }
    }

    private void startVoice() {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Aria");
            startActivityForResult(i, REQ_VOICE);
        } catch (Exception e) {
            addBubble(Message.ASSISTANT, "Voice input isn't available on this device.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> res = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            if (res != null && !res.isEmpty()) {
                input.setText(res.get(0));
                sendCurrent();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.shutdown();
        }
        super.onDestroy();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
