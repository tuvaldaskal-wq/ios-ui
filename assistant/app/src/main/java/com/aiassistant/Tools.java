package com.aiassistant;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.telephony.SmsManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * The assistant's "hands": tool definitions exposed to the model and their
 * on-device execution. Everything is done with framework APIs so it can drive
 * the phone (open apps, message, call, alarms, web) with no extra libraries.
 */
public final class Tools {

    private final Context ctx;

    public Tools(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** Tool schemas in Anthropic's tool format. */
    public static JSONArray schemas() {
        JSONArray tools = new JSONArray();
        tools.put(tool("open_app", "Open/launch an installed app by its name (e.g. 'Brawl Stars', 'WhatsApp', 'Settings').",
                prop("app_name", "Name of the app to open"), "app_name"));
        tools.put(tool("list_apps", "List the apps installed on the phone. Use this if you are unsure of an app's exact name.",
                new JSONObject(), null));
        tools.put(tool("send_sms", "Send a text message (SMS) silently in the background.",
                props2("to", "A phone number or a contact name (e.g. 'Mom')",
                        "message", "The text to send"), "to,message"));
        tools.put(tool("send_whatsapp", "Send a WhatsApp message to a contact/number. Opens the chat with the text pre-filled and (if Aria's accessibility is enabled) taps Send automatically.",
                props2("to", "A phone number or contact name",
                        "message", "The message text"), "to,message"));
        tools.put(tool("call", "Start a phone call to a number or contact.",
                prop("to", "A phone number or contact name"), "to"));
        tools.put(tool("set_timer", "Start a countdown timer. Use this for durations like 'in 10 minutes'.",
                propInt("seconds", "Length of the timer in seconds"), "seconds"));
        tools.put(tool("set_alarm", "Set an alarm for a clock time (e.g. '7am' = hour 7, minute 0). Use get_device_info first if you need the current time for a relative request.",
                props3int("hour", "Hour 0-23", "minute", "Minute 0-59",
                        "label", false), "hour,minute"));
        tools.put(tool("flashlight", "Turn the phone flashlight (torch) on or off.",
                propBool("on", "true to turn on, false to turn off"), "on"));
        tools.put(tool("set_volume", "Set the media volume.",
                propInt("percent", "Volume from 0 to 100"), "percent"));
        tools.put(tool("navigate", "Start turn-by-turn navigation to a place or address in Maps.",
                prop("destination", "Where to navigate to"), "destination"));
        tools.put(tool("send_email", "Compose an email (opens the email app pre-filled to send).",
                props3("to", "Recipient email address",
                        "subject", "Email subject", "body", "Email body"), "to"));
        tools.put(tool("get_device_info", "Get the current time, date and battery level. Use this when you need 'now' to compute alarms/timers or to answer time/battery questions.",
                new JSONObject(), null));
        tools.put(tool("web_search", "Search the web.",
                prop("query", "What to search for"), "query"));
        tools.put(tool("open_url", "Open a web page / URL in the browser.",
                prop("url", "The URL to open"), "url"));
        return tools;
    }

    /** Run a tool and return a short human-readable result for the model. */
    public String execute(String name, JSONObject in) {
        try {
            if ("open_app".equals(name)) {
                return openApp(in.optString("app_name"));
            } else if ("list_apps".equals(name)) {
                return listApps();
            } else if ("send_sms".equals(name)) {
                return sendSms(in.optString("to"), in.optString("message"));
            } else if ("send_whatsapp".equals(name)) {
                return sendWhatsApp(in.optString("to"), in.optString("message"));
            } else if ("call".equals(name)) {
                return call(in.optString("to"));
            } else if ("set_timer".equals(name)) {
                return setTimer(in.optInt("seconds"));
            } else if ("set_alarm".equals(name)) {
                return setAlarm(in.optInt("hour"), in.optInt("minute"), in.optString("label"));
            } else if ("flashlight".equals(name)) {
                return flashlight(in.optBoolean("on"));
            } else if ("set_volume".equals(name)) {
                return setVolume(in.optInt("percent"));
            } else if ("navigate".equals(name)) {
                return navigate(in.optString("destination"));
            } else if ("send_email".equals(name)) {
                return sendEmail(in.optString("to"), in.optString("subject"), in.optString("body"));
            } else if ("get_device_info".equals(name)) {
                return deviceInfo();
            } else if ("web_search".equals(name)) {
                return webSearch(in.optString("query"));
            } else if ("open_url".equals(name)) {
                return openUrl(in.optString("url"));
            }
            return "Unknown tool: " + name;
        } catch (Exception e) {
            return "Error running " + name + ": " + e.getMessage();
        }
    }

    // --- individual tools ---------------------------------------------

    private String openApp(String query) {
        ResolveInfo best = findApp(query);
        if (best == null) {
            return "No installed app matches \"" + query + "\".";
        }
        String pkg = best.activityInfo.packageName;
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            return "Found " + query + " but it can't be launched.";
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(launch);
        return "Opened " + best.loadLabel(ctx.getPackageManager()) + ".";
    }

    private ResolveInfo findApp(String query) {
        if (query == null) {
            return null;
        }
        String q = query.trim().toLowerCase();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = ctx.getPackageManager().queryIntentActivities(main, 0);
        ResolveInfo exact = null;
        ResolveInfo contains = null;
        for (int i = 0; i < apps.size(); i++) {
            String label = String.valueOf(apps.get(i).loadLabel(ctx.getPackageManager())).toLowerCase();
            if (label.equals(q)) {
                exact = apps.get(i);
                break;
            }
            if (contains == null && (label.contains(q) || q.contains(label))) {
                contains = apps.get(i);
            }
        }
        return exact != null ? exact : contains;
    }

    private String listApps() {
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = ctx.getPackageManager().queryIntentActivities(main, 0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < apps.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(apps.get(i).loadLabel(ctx.getPackageManager()));
        }
        return sb.toString();
    }

    private String sendSms(String to, String message) {
        if (!hasPermission(android.Manifest.permission.SEND_SMS)) {
            return "I don't have SMS permission yet - please grant it and try again.";
        }
        String number = resolveNumber(to);
        if (number == null) {
            return "I couldn't find a phone number for \"" + to + "\".";
        }
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(number, null, message, null, null);
        return "Sent the text to " + to + ".";
    }

    private String sendWhatsApp(String to, String message) {
        String number = resolveNumber(to);
        if (number == null) {
            return "I couldn't find a WhatsApp number for \"" + to + "\".";
        }
        String digits = number.replaceAll("[^0-9]", "");
        Uri uri = Uri.parse("https://wa.me/" + digits + "?text=" + Uri.encode(message));
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        i.setPackage("com.whatsapp");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Arm the accessibility service to tap Send automatically (if enabled).
        AriaAccessibilityService.armWhatsAppSend();
        try {
            ctx.startActivity(i);
        } catch (Exception e) {
            i.setPackage(null);
            ctx.startActivity(i);
        }
        return "Messaged " + to + " on WhatsApp.";
    }

    private String flashlight(boolean on) {
        try {
            android.hardware.camera2.CameraManager cm =
                    (android.hardware.camera2.CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            String[] ids = cm.getCameraIdList();
            for (int i = 0; i < ids.length; i++) {
                Boolean hasFlash = cm.getCameraCharacteristics(ids[i])
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(hasFlash)) {
                    cm.setTorchMode(ids[i], on);
                    return on ? "Flashlight on." : "Flashlight off.";
                }
            }
            return "No flashlight on this device.";
        } catch (Exception e) {
            return "Couldn't toggle the flashlight.";
        }
    }

    private String setVolume(int percent) {
        if (percent < 0) {
            percent = 0;
        }
        if (percent > 100) {
            percent = 100;
        }
        android.media.AudioManager am =
                (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                Math.round(max * (percent / 100f)), 0);
        return "Volume set to " + percent + "%.";
    }

    private String navigate(String destination) {
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("google.navigation:q=" + Uri.encode(destination)));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (Exception e) {
            return openUrl("https://www.google.com/maps/dir/?api=1&destination="
                    + Uri.encode(destination));
        }
        return "Navigating to " + destination + ".";
    }

    private String sendEmail(String to, String subject, String body) {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"
                + (to == null ? "" : to)));
        if (subject != null) {
            i.putExtra(Intent.EXTRA_SUBJECT, subject);
        }
        if (body != null) {
            i.putExtra(Intent.EXTRA_TEXT, body);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        return "Opened an email to " + to + " ready to send.";
    }

    private String deviceInfo() {
        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("EEEE, d MMM yyyy, HH:mm", java.util.Locale.getDefault());
        String now = fmt.format(new java.util.Date());
        String battery = "";
        try {
            android.os.BatteryManager bm =
                    (android.os.BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            int level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
            battery = ", battery " + level + "%";
        } catch (Exception ignored) {
        }
        return "Current time: " + now + battery + ".";
    }

    private String call(String to) {
        String number = resolveNumber(to);
        if (number == null) {
            return "I couldn't find a number for \"" + to + "\".";
        }
        boolean canCall = hasPermission(android.Manifest.permission.CALL_PHONE);
        Intent i = new Intent(canCall ? Intent.ACTION_CALL : Intent.ACTION_DIAL,
                Uri.parse("tel:" + number));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        return (canCall ? "Calling " : "Opening the dialer for ") + to + ".";
    }

    private String setTimer(int seconds) {
        Intent i = new Intent(AlarmClock.ACTION_SET_TIMER);
        i.putExtra(AlarmClock.EXTRA_LENGTH, seconds);
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        return "Timer set for " + seconds + " seconds.";
    }

    private String setAlarm(int hour, int minute, String label) {
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
        i.putExtra(AlarmClock.EXTRA_HOUR, hour);
        i.putExtra(AlarmClock.EXTRA_MINUTES, minute);
        if (label != null && label.length() > 0) {
            i.putExtra(AlarmClock.EXTRA_MESSAGE, label);
        }
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        return "Alarm set for " + hour + ":" + (minute < 10 ? "0" + minute : minute) + ".";
    }

    private String webSearch(String query) {
        Intent i = new Intent(Intent.ACTION_WEB_SEARCH);
        i.putExtra("query", query);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (Exception e) {
            return openUrl("https://www.google.com/search?q=" + Uri.encode(query));
        }
        return "Searching the web for \"" + query + "\".";
    }

    private String openUrl(String url) {
        if (url == null || url.length() == 0) {
            return "No URL given.";
        }
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        return "Opened " + url + ".";
    }

    // --- helpers ------------------------------------------------------

    private String resolveNumber(String to) {
        if (to == null || to.length() == 0) {
            return null;
        }
        String trimmed = to.trim();
        if (trimmed.matches("[+0-9 ()-]{4,}")) {
            return trimmed.replaceAll("[^+0-9]", "");
        }
        if (!hasPermission(android.Manifest.permission.READ_CONTACTS)) {
            return null;
        }
        Cursor c = null;
        try {
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String sel = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
            c = ctx.getContentResolver().query(uri,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                    sel, new String[]{"%" + trimmed + "%"}, null);
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    private boolean hasPermission(String perm) {
        return ctx.checkPermission(perm, android.os.Process.myPid(), android.os.Process.myUid())
                == PackageManager.PERMISSION_GRANTED;
    }

    // --- tiny schema builders -----------------------------------------

    private static JSONObject tool(String name, String desc, JSONObject properties, String required) {
        try {
            JSONObject t = new JSONObject();
            t.put("name", name);
            t.put("description", desc);
            JSONObject schema = new JSONObject();
            schema.put("type", "object");
            schema.put("properties", properties);
            if (required != null) {
                JSONArray req = new JSONArray();
                for (String r : required.split(",")) {
                    req.put(r);
                }
                schema.put("required", req);
            }
            t.put("input_schema", schema);
            return t;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONObject prop(String name, String desc) {
        try {
            JSONObject p = new JSONObject();
            p.put(name, field("string", desc));
            return p;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONObject propInt(String name, String desc) {
        try {
            JSONObject p = new JSONObject();
            p.put(name, field("integer", desc));
            return p;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONObject propBool(String name, String desc) {
        try {
            JSONObject p = new JSONObject();
            p.put(name, field("boolean", desc));
            return p;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONObject props3(String n1, String d1, String n2, String d2,
                                     String n3, String d3) {
        try {
            JSONObject p = new JSONObject();
            p.put(n1, field("string", d1));
            p.put(n2, field("string", d2));
            p.put(n3, field("string", d3));
            return p;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONObject props2(String n1, String d1, String n2, String d2) {
        try {
            JSONObject p = new JSONObject();
            p.put(n1, field("string", d1));
            p.put(n2, field("string", d2));
            return p;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONObject props3int(String n1, String d1, String n2, String d2,
                                        String n3, boolean unusedString) {
        try {
            JSONObject p = new JSONObject();
            p.put(n1, field("integer", d1));
            p.put(n2, field("integer", d2));
            p.put("label", field("string", "Optional label"));
            return p;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONObject field(String type, String desc) throws Exception {
        JSONObject f = new JSONObject();
        f.put("type", type);
        f.put("description", desc);
        return f;
    }
}
