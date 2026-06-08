package com.aiassistant;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Lets Aria auto-tap WhatsApp's Send button after a chat is opened pre-filled,
 * so the user doesn't have to. Armed by Tools.sendWhatsApp(); it clicks Send the
 * moment the WhatsApp window appears, then disarms. (Android has no silent
 * WhatsApp API — this is the closest: automated, not invisible.)
 */
public class AriaAccessibilityService extends AccessibilityService {

    private static volatile boolean armWhatsApp = false;

    public static void armWhatsAppSend() {
        armWhatsApp = true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!armWhatsApp || event == null || event.getPackageName() == null) {
            return;
        }
        String pkg = event.getPackageName().toString();
        if (!pkg.startsWith("com.whatsapp")) {
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }
        if (clickById(root, pkg + ":id/send")) {
            armWhatsApp = false;
        }
    }

    private boolean clickById(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        if (nodes == null || nodes.isEmpty()) {
            return false;
        }
        for (int i = 0; i < nodes.size(); i++) {
            AccessibilityNodeInfo n = nodes.get(i);
            if (n == null) {
                continue;
            }
            if (n.isClickable()) {
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
            AccessibilityNodeInfo p = n.getParent();
            if (p != null && p.isClickable()) {
                p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onInterrupt() {
    }
}
