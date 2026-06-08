package com.aiassistant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Minimal Anthropic Messages API client built on the framework's HTTP stack
 * (no third-party libraries). If a backend URL is configured the request is
 * sent there instead (the backend holds the key and checks the subscription).
 */
public class AnthropicClient {

    private final Config config;

    public AnthropicClient(Config config) {
        this.config = config;
    }

    /** One round-trip to the model. Returns the parsed response JSON. */
    public JSONObject createMessage(JSONArray messages, JSONArray tools, String system)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", config.model);
        body.put("max_tokens", 1024);
        if (system != null) {
            body.put("system", system);
        }
        body.put("messages", messages);
        if (tools != null) {
            body.put("tools", tools);
        }

        URL url;
        boolean backend = config.useBackend();
        if (backend) {
            url = new URL(config.backendUrl.replaceAll("/+$", "") + "/v1/messages");
        } else {
            url = new URL("https://api.anthropic.com/v1/messages");
        }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (!backend) {
            conn.setRequestProperty("x-api-key", config.apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
        }

        byte[] out = body.toString().getBytes("UTF-8");
        OutputStream os = conn.getOutputStream();
        os.write(out);
        os.close();

        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String resp = readAll(is);
        conn.disconnect();

        if (code < 200 || code >= 300) {
            JSONObject err = new JSONObject();
            err.put("__http_error", code);
            err.put("__body", resp);
            return err;
        }
        return new JSONObject(resp);
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) {
            return "";
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
        }
        r.close();
        return sb.toString();
    }
}
