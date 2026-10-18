package it.sculptura.jarvis;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class JarvisAccessibilityService extends AccessibilityService {
    private static WeakReference<JarvisAccessibilityService> current = new WeakReference<>(null);

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        current = new WeakReference<>(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        super.onDestroy();
        current = new WeakReference<>(null);
    }

    public static boolean isActive() {
        return current.get() != null;
    }

    public static boolean globalBack() {
        JarvisAccessibilityService s = current.get();
        return s != null && s.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public static boolean globalHome() {
        JarvisAccessibilityService s = current.get();
        return s != null && s.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public static boolean globalRecents() {
        JarvisAccessibilityService s = current.get();
        return s != null && s.performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    public static boolean globalNotifications() {
        JarvisAccessibilityService s = current.get();
        return s != null && s.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    public static boolean clickText(String text) {
        JarvisAccessibilityService s = current.get();
        if (s == null || text == null || text.trim().isEmpty()) return false;
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return false;
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text.trim());
            if (nodes == null) return false;
            for (AccessibilityNodeInfo node : nodes) {
                AccessibilityNodeInfo candidate = node;
                while (candidate != null) {
                    if (candidate.isClickable() && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true;
                    }
                    candidate = candidate.getParent();
                }
            }
        } finally {
            root.recycle();
        }
        return false;
    }

    public static boolean writeText(String text) {
        JarvisAccessibilityService s = current.get();
        if (s == null || text == null) return false;
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return false;
        try {
            AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (target == null || !target.isEditable()) {
                target = findEditable(root);
            }
            if (target == null) return false;
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } finally {
            root.recycle();
        }
    }

    private static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo root) {
        Deque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node.isEditable()) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        return null;
    }
}
