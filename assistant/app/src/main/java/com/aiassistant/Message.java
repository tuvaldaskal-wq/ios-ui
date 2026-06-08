package com.aiassistant;

/** A chat bubble shown in the UI. */
public class Message {
    public static final int USER = 0;
    public static final int ASSISTANT = 1;
    public static final int STATUS = 2;

    public final int type;
    public String text;

    public Message(int type, String text) {
        this.type = type;
        this.text = text;
    }
}
