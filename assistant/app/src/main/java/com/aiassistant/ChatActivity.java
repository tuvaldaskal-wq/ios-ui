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
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

/** Premium chat screen for the Aria assistant (also usable as a Home app). */
public class ChatActivity extends Activity {

    private static final int REQ_VOICE = 100;
    private static final int REQ_PERMS = 101;

    private Config config;
    private Agent agent;
    private TextToSpeech tts;
    private boolean ttsReady;
    private Avatar avatar;
    private View headerOrb;

    private ScrollView scroll;
    private LinearLayout messages;
    private EditText input;
    private View statusRow;
    private TextView statusLabel;
    private View chipsRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        config = new Config(this);
        agent = new Agent(this, config);
        avatar = AvatarPrefs.get(this);
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
        showGreeting();
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

        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Messages
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(14), dp(8), dp(14), dp(10));
        scroll.addView(messages);
        scroll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    hideKeyboard();
                }
                return false;
            }
        });
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildInputBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(12), dp(18), dp(12));

        View orb = new View(this);
        orb.setBackground(avatar.makeDrawable(this, 40));
        orb.setElevation(dp(4));
        orb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ChatActivity.this, AvatarActivity.class));
            }
        });
        LinearLayout.LayoutParams orbLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        orbLp.rightMargin = dp(12);
        header.addView(orb, orbLp);
        headerOrb = orb;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Aria");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20f);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        col.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Your AI assistant");
        sub.setTextColor(Color.parseColor("#80FFFFFF"));
        sub.setTextSize(12f);
        col.addView(sub);
        header.addView(col, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        return header;
    }

    private View buildInputBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.BOTTOM);
        bar.setPadding(dp(12), dp(8), dp(12), dp(14));

        input = new EditText(this);
        input.setHint("Message Aria…");
        input.setHintTextColor(Color.parseColor("#80FFFFFF"));
        input.setTextColor(Color.WHITE);
        input.setTextSize(16f);
        input.setBackgroundResource(R.drawable.input_bg);
        input.setPadding(dp(18), dp(12), dp(18), dp(12));
        input.setMaxLines(5);
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
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inLp.gravity = Gravity.BOTTOM;
        bar.addView(input, inLp);

        TextView mic = circleButton("🎤", R.drawable.mic_button, 17f);
        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startVoice();
            }
        });
        bar.addView(mic, circleParams(dp(8)));

        TextView send = circleButton("↑", R.drawable.send_button, 20f);
        send.setElevation(dp(3));
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCurrent();
            }
        });
        bar.addView(send, circleParams(dp(8)));
        return bar;
    }

    private TextView circleButton(String glyph, int bgRes, float textSize) {
        TextView t = new TextView(this);
        t.setText(glyph);
        t.setTextColor(Color.WHITE);
        t.setTextSize(textSize);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(bgRes);
        return t;
    }

    private LinearLayout.LayoutParams circleParams(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.leftMargin = leftMargin;
        lp.gravity = Gravity.BOTTOM;
        return lp;
    }

    // ------------------------------------------------------------------
    // Greeting + suggestion chips
    // ------------------------------------------------------------------

    private void showGreeting() {
        if (config.isConfigured()) {
            addBubble(Message.ASSISTANT,
                    "Hi, I'm Aria 👋\nAsk me anything, or just tell me what to do.");
            addChips(new String[]{
                    "Open WhatsApp", "Text Mom I'm on my way",
                    "Set a 10 minute timer", "What's the weather like?",
                    "Open Brawl Stars"});
        } else {
            addBubble(Message.ASSISTANT,
                    "Hi, I'm Aria 👋\nTo get started, add your Anthropic API key to "
                    + "local.properties (ANTHROPIC_API_KEY=…) and rebuild. Then I can open "
                    + "apps, text people, set timers and more.");
        }
    }

    private void addChips(String[] labels) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(2), dp(8), dp(2), dp(6));
        for (int i = 0; i < labels.length; i++) {
            final String label = labels[i];
            TextView chip = new TextView(this);
            chip.setText(label);
            chip.setTextColor(Color.parseColor("#E6FFFFFF"));
            chip.setTextSize(13.5f);
            chip.setBackgroundResource(R.drawable.chip_bg);
            chip.setPadding(dp(16), dp(10), dp(16), dp(10));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    input.setText(label);
                    sendCurrent();
                }
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.rightMargin = dp(8);
            row.addView(chip, clp);
        }
        hs.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(36);
        messages.addView(hs, lp);
        chipsRow = hs;
        animateIn(hs);
    }

    // ------------------------------------------------------------------
    // Bubbles
    // ------------------------------------------------------------------

    private TextView addBubble(int type, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(type == Message.USER ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(5);
        rowLp.bottomMargin = dp(5);
        row.setLayoutParams(rowLp);

        if (type != Message.USER) {
            row.addView(makeAvatar());
        }

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(16f);
        bubble.setTextColor(Color.WHITE);
        bubble.setLineSpacing(dp(3), 1f);
        bubble.setPadding(dp(15), dp(11), dp(15), dp(11));
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.76));
        bubble.setBackgroundResource(type == Message.USER
                ? R.drawable.bubble_user : R.drawable.bubble_assistant);
        bubble.setElevation(dp(2));

        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (type != Message.USER) {
            bLp.leftMargin = dp(8);
        }
        row.addView(bubble, bLp);

        messages.addView(row);
        animateIn(row);
        scrollDown();
        return bubble;
    }

    private View makeAvatar() {
        View a = new View(this);
        a.setBackground(avatar.makeDrawable(this, 28));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(28));
        lp.topMargin = dp(2);
        a.setLayoutParams(lp);
        return a;
    }

    private void showTyping() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(5);
        rowLp.bottomMargin = dp(5);
        row.setLayoutParams(rowLp);
        row.addView(makeAvatar());

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.HORIZONTAL);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setBackgroundResource(R.drawable.bubble_assistant);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.addView(new TypingDotsView(this));

        statusLabel = new TextView(this);
        statusLabel.setTextColor(Color.parseColor("#CCFFFFFF"));
        statusLabel.setTextSize(14f);
        statusLabel.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(8);
        bubble.addView(statusLabel, slp);

        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.leftMargin = dp(8);
        row.addView(bubble, bLp);

        messages.addView(row);
        animateIn(row);
        scrollDown();
        statusRow = row;
    }

    private void animateIn(View v) {
        v.setAlpha(0f);
        v.setTranslationY(dp(14));
        v.animate().alpha(1f).translationY(0f).setDuration(220).start();
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
            statusLabel = null;
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
        } catch (Exception ignored) {
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
        if (chipsRow != null) {
            messages.removeView(chipsRow);
            chipsRow = null;
        }
        addBubble(Message.USER, text);

        if (!config.isConfigured()) {
            addBubble(Message.ASSISTANT,
                    "I need an API key first — add ANTHROPIC_API_KEY to local.properties "
                    + "and rebuild.");
            return;
        }

        showTyping();
        agent.send(text, new Agent.Listener() {
            @Override
            public void onStatus(String note) {
                if (statusLabel != null) {
                    statusLabel.setText(note);
                    statusLabel.setVisibility(View.VISIBLE);
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
    protected void onResume() {
        super.onResume();
        Avatar fresh = AvatarPrefs.get(this);
        if (!fresh.id.equals(avatar.id)) {
            avatar = fresh;
            if (headerOrb != null) {
                headerOrb.setBackground(avatar.makeDrawable(this, 40));
            }
        }
    }

    /** As a Home app, don't exit on Back — just dismiss the keyboard. */
    @Override
    public void onBackPressed() {
        hideKeyboard();
        if (input != null) {
            input.clearFocus();
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
