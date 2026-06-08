package com.aiassistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

/** Black screen with a glowing orb that wiggles to the rhythm of speech. */
public class VoiceActivity extends Activity {

    private static final int REQ_PERMS = 201;

    private Config config;
    private Agent agent;
    private TextToSpeech tts;
    private boolean ttsReady;

    private OrbView orb;
    private TextView caption;
    private TextView hint;
    private SpeechRecognizer recognizer;
    private boolean listening;

    private FrameLayout root;
    private Billing billing;
    private View paywall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Require login when Supabase is configured.
        if (Supabase.isConfigured(this) && SupabaseAuth.getToken(this) == null) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        config = new Config(this);
        agent = new Agent(this, config);
        setContentView(buildUi());
        setupGate();

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ttsReady = status == TextToSpeech.SUCCESS;
                if (ttsReady) {
                    tts.setLanguage(Locale.getDefault());
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String id) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    orb.setState(OrbView.SPEAKING);
                                }
                            });
                        }
                        @Override
                        public void onDone(String id) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    orb.setState(OrbView.IDLE);
                                }
                            });
                        }
                        @Override
                        public void onError(String id) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    orb.setState(OrbView.IDLE);
                                }
                            });
                        }
                    });
                }
            }
        });

        requestPerms();

        if (!config.isConfigured()) {
            caption.setText("Add your API key (or sign in to a configured build) to start.");
        }
    }

    private void requestPerms() {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            return;
        }
        try {
            requestPermissions(new String[]{
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.SEND_SMS,
                    android.Manifest.permission.CALL_PHONE,
                    android.Manifest.permission.READ_CONTACTS
            }, REQ_PERMS);
        } catch (Exception ignored) {
        }
    }

    private View buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setFitsSystemWindows(true);

        orb = new OrbView(this);
        int size = (int) (getResources().getDisplayMetrics().widthPixels * 0.8);
        FrameLayout.LayoutParams orbLp = new FrameLayout.LayoutParams(size, size);
        orbLp.gravity = Gravity.CENTER;
        orb.setLayoutParams(orbLp);
        orb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleListen();
            }
        });
        root.addView(orb);

        hint = new TextView(this);
        hint.setText("Tap the orb to talk");
        hint.setTextColor(Color.parseColor("#80FFFFFF"));
        hint.setTextSize(15f);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hintLp.topMargin = dp(64);
        root.addView(hint, hintLp);

        caption = new TextView(this);
        caption.setTextColor(Color.parseColor("#E6FFFFFF"));
        caption.setTextSize(17f);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(dp(28), 0, dp(28), 0);
        FrameLayout.LayoutParams capLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        capLp.bottomMargin = dp(80);
        root.addView(caption, capLp);

        // Keyboard button → text chat
        TextView keyboard = corner("⌨", Gravity.TOP | Gravity.END, dp(14));
        keyboard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(VoiceActivity.this, ChatActivity.class));
            }
        });
        root.addView(keyboard);

        return root;
    }

    private TextView corner(String glyph, int gravity, int margin) {
        TextView t = new TextView(this);
        t.setText(glyph);
        t.setTextColor(Color.WHITE);
        t.setTextSize(22f);
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = gravity;
        lp.topMargin = margin;
        lp.rightMargin = margin;
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------------
    // Listening
    // ------------------------------------------------------------------

    private void toggleListen() {
        if (listening) {
            stopListen();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            caption.setText("Voice recognition isn't available — tap ⌨ to type.");
            return;
        }
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(listener);
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            listening = true;
            hint.setText("Listening…");
            orb.setState(OrbView.LISTENING);
            recognizer.startListening(i);
        } catch (Exception e) {
            caption.setText("Couldn't start listening.");
            stopListen();
        }
    }

    private void stopListen() {
        listening = false;
        hint.setText("Tap the orb to talk");
        if (recognizer != null) {
            try {
                recognizer.stopListening();
                recognizer.destroy();
            } catch (Exception ignored) {
            }
            recognizer = null;
        }
        orb.setLevel(0f);
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
            orb.setState(OrbView.LISTENING);
        }
        @Override
        public void onRmsChanged(float rmsdB) {
            float level = (rmsdB + 2f) / 12f; // ~ -2..10 dB -> 0..1
            orb.setLevel(level);
        }
        @Override
        public void onResults(Bundle results) {
            ArrayList<String> list = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            stopListen();
            if (list != null && !list.isEmpty()) {
                process(list.get(0));
            }
        }
        @Override
        public void onPartialResults(Bundle partial) {
            ArrayList<String> list = partial.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            if (list != null && !list.isEmpty()) {
                caption.setText(list.get(0));
            }
        }
        @Override
        public void onError(int error) {
            stopListen();
            orb.setState(OrbView.IDLE);
        }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onEndOfSpeech() { orb.setState(OrbView.THINKING); }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEvent(int eventType, Bundle params) { }
    };

    private void process(final String text) {
        caption.setText(text);
        if (!config.isConfigured()) {
            caption.setText("No API key / sign-in configured.");
            return;
        }
        orb.setState(OrbView.THINKING);
        log("user", text);
        agent.send(text, new Agent.Listener() {
            @Override
            public void onStatus(String note) {
                caption.setText(note);
            }
            @Override
            public void onResult(String reply) {
                caption.setText(reply);
                log("assistant", reply);
                speak(reply);
            }
            @Override
            public void onError(String message) {
                orb.setState(OrbView.IDLE);
                caption.setText("⚠ " + message);
            }
        });
    }

    /** Log a turn to the signed-in account (powers the admin panel). */
    private void log(final String role, final String content) {
        if (!Supabase.isConfigured(this) || content == null || content.isEmpty()) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                SupabaseDb.logMessage(VoiceActivity.this, role, content);
            }
        }).start();
    }

    private void speak(String text) {
        if (ttsReady && text != null && text.length() > 0) {
            orb.setState(OrbView.SPEAKING);
            tts.speak(text.length() > 600 ? text.substring(0, 600) : text,
                    TextToSpeech.QUEUE_FLUSH, null, "aria");
        } else {
            orb.setState(OrbView.IDLE);
        }
    }

    @Override
    public void onBackPressed() {
        // Home/voice screen — don't exit.
        if (listening) {
            stopListen();
        }
    }

    // ------------------------------------------------------------------
    // Subscription gate (entitlement lives on the signed-in account)
    // ------------------------------------------------------------------

    private void setupGate() {
        final boolean useSupabase = Supabase.isConfigured(this);
        if (Billing.enabled()) {
            billing = new Billing(this, new Billing.Listener() {
                @Override
                public void onSubscriptionChanged(boolean subscribed) {
                    if (subscribed) {
                        if (useSupabase) {
                            saveProThenHide();   // store entitlement on the account
                        } else {
                            hidePaywall();
                        }
                    }
                }
            });
            billing.start();
        }
        if (useSupabase) {
            showPaywall();
            checkPlan();
        } else if (Billing.enabled()) {
            showPaywall();
        }
        // else: no gate (local/dev build)
    }

    /** Unlock if the signed-in account is subscribed (pro) OR an admin. */
    private void checkPlan() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean entitled = SupabaseDb.isEntitled(VoiceActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (entitled) {
                            hidePaywall();
                        } else {
                            showPaywall();
                        }
                    }
                });
            }
        }).start();
    }

    private void saveProThenHide() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                SupabaseDb.setPlanPro(VoiceActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hidePaywall();
                    }
                });
            }
        }).start();
    }

    private void signOut() {
        SupabaseAuth.signOut(this);
        startActivity(new Intent(this, AuthActivity.class));
        finish();
    }

    // ------------------------------------------------------------------
    // Subscription paywall
    // ------------------------------------------------------------------

    private void showPaywall() {
        if (paywall != null) {
            return;
        }
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER);
        p.setBackgroundColor(Color.parseColor("#F2000000"));
        p.setClickable(true);
        p.setPadding(dp(32), dp(32), dp(32), dp(32));

        View orbIcon = new View(this);
        orbIcon.setBackgroundResource(R.drawable.orb);
        LinearLayout.LayoutParams oi = new LinearLayout.LayoutParams(dp(84), dp(84));
        oi.bottomMargin = dp(20);
        p.addView(orbIcon, oi);

        TextView title = new TextView(this);
        title.setText("Aria Premium");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26f);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        p.addView(title);

        TextView desc = new TextView(this);
        desc.setText("Subscribe to unlock your AI assistant.\nYour plan stays on your "
                + "Google account, so it's restored automatically if you reinstall.");
        desc.setTextColor(Color.parseColor("#B3FFFFFF"));
        desc.setTextSize(14f);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(10), 0, dp(26));
        p.addView(desc);

        TextView subscribe = new TextView(this);
        subscribe.setText("Subscribe");
        subscribe.setTextColor(Color.WHITE);
        subscribe.setTextSize(17f);
        subscribe.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        subscribe.setGravity(Gravity.CENTER);
        subscribe.setBackgroundResource(R.drawable.send_button);
        subscribe.setPadding(dp(48), dp(15), dp(48), dp(15));
        subscribe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (billing != null) {
                    billing.subscribe();
                }
            }
        });
        p.addView(subscribe);

        TextView restore = new TextView(this);
        restore.setText("Restore");
        restore.setTextColor(Color.parseColor("#99FFFFFF"));
        restore.setTextSize(14f);
        restore.setPadding(dp(16), dp(18), dp(16), dp(8));
        restore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (billing != null) {
                    billing.refresh();
                }
                if (Supabase.isConfigured(VoiceActivity.this)) {
                    checkPlan();
                }
            }
        });
        p.addView(restore);

        if (Supabase.isConfigured(this)) {
            TextView out = new TextView(this);
            out.setText("Sign out");
            out.setTextColor(Color.parseColor("#66FFFFFF"));
            out.setTextSize(13f);
            out.setPadding(dp(16), dp(6), dp(16), dp(8));
            out.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    signOut();
                }
            });
            p.addView(out);
        }

        paywall = p;
        root.addView(paywall, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void hidePaywall() {
        if (paywall != null) {
            root.removeView(paywall);
            paywall = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.shutdown();
        }
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Exception ignored) {
            }
        }
        if (billing != null) {
            billing.destroy();
        }
        super.onDestroy();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
