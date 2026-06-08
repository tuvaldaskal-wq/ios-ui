package com.aiassistant;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Runs the agentic loop: send the conversation to the model, execute any tool
 * calls it requests on the device, feed the results back, and repeat until the
 * model returns a final text answer.
 */
public class Agent {

    public interface Listener {
        void onStatus(String note);
        void onResult(String text);
        void onError(String message);
    }

    private static final String SYSTEM =
            "You are Aria, a helpful voice/chat assistant living inside an Android phone app. "
            + "You can actually operate the phone using the provided tools: open apps, send SMS, "
            + "compose WhatsApp messages, place calls, set alarms and timers, and search the web. "
            + "When the user asks you to do something on the phone, call the right tool, then tell "
            + "them briefly and warmly what you did (e.g. \"Done - texted Mom 'I'm getting "
            + "groceries!'\"). If you need a contact's number and can't find it, ask. Keep replies "
            + "short and natural. Answer general questions directly without tools.";

    private final Config config;
    private final AnthropicClient client;
    private final Tools tools;
    private final Context appContext;
    private final JSONArray history = new JSONArray();
    private final Handler main = new Handler(Looper.getMainLooper());

    public Agent(Context ctx, Config config) {
        this.config = config;
        this.client = new AnthropicClient(config);
        this.tools = new Tools(ctx);
        this.appContext = ctx.getApplicationContext();
    }

    public void send(final String userText, final Listener listener) {
        try {
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userText);
            history.put(userMsg);
        } catch (Exception e) {
            post(listener, 2, e.getMessage());
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                runLoop(listener);
            }
        }).start();
    }

    private void runLoop(Listener listener) {
        try {
            for (int step = 0; step < 6; step++) {
                JSONObject resp = client.createMessage(history, Tools.schemas(), SYSTEM);
                if (resp.has("__http_error")) {
                    post(listener, 2, "API error " + resp.optInt("__http_error") + ": "
                            + truncate(resp.optString("__body"), 300));
                    return;
                }
                JSONArray content = resp.optJSONArray("content");
                if (content == null) {
                    post(listener, 2, "Empty response from the model.");
                    return;
                }

                JSONObject assistantMsg = new JSONObject();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", content);
                history.put(assistantMsg);

                StringBuilder text = new StringBuilder();
                JSONArray toolResults = new JSONArray();
                for (int i = 0; i < content.length(); i++) {
                    JSONObject block = content.getJSONObject(i);
                    String type = block.optString("type");
                    if ("text".equals(type)) {
                        text.append(block.optString("text"));
                    } else if ("tool_use".equals(type)) {
                        String name = block.optString("name");
                        String id = block.optString("id");
                        JSONObject input = block.optJSONObject("input");
                        if (input == null) {
                            input = new JSONObject();
                        }
                        post(listener, 0, statusFor(name, input));
                        String result = tools.execute(name, input);
                        JSONObject tr = new JSONObject();
                        tr.put("type", "tool_result");
                        tr.put("tool_use_id", id);
                        tr.put("content", result);
                        toolResults.put(tr);
                    }
                }

                if (toolResults.length() > 0) {
                    JSONObject toolMsg = new JSONObject();
                    toolMsg.put("role", "user");
                    toolMsg.put("content", toolResults);
                    history.put(toolMsg);
                    // loop again so the model can respond to the tool results
                } else {
                    post(listener, 1, text.toString().trim());
                    return;
                }
            }
            post(listener, 1, "I took a few steps but didn't finish - try rephrasing?");
        } catch (Exception e) {
            post(listener, 2, "Something went wrong: " + e.getMessage());
        }
    }

    private String statusFor(String tool, JSONObject in) {
        if ("open_app".equals(tool)) {
            return "Opening " + in.optString("app_name") + "…";
        }
        if ("send_sms".equals(tool)) {
            return "Texting " + in.optString("to") + "…";
        }
        if ("send_whatsapp".equals(tool)) {
            return "Messaging " + in.optString("to") + " on WhatsApp…";
        }
        if ("call".equals(tool)) {
            return "Calling " + in.optString("to") + "…";
        }
        if ("set_timer".equals(tool)) {
            return "Setting a timer…";
        }
        if ("set_alarm".equals(tool)) {
            return "Setting an alarm…";
        }
        if ("web_search".equals(tool)) {
            return "Searching the web…";
        }
        return "Working…";
    }

    private void post(final Listener l, final int kind, final String msg) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (kind == 0) {
                    l.onStatus(msg);
                } else if (kind == 1) {
                    l.onResult(msg);
                } else {
                    l.onError(msg);
                }
            }
        });
    }

    private static String truncate(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
