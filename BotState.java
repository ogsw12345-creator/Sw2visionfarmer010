package com.openai.sf2farmer;

public final class BotState {
    private BotState() {}
    public static volatile boolean running = false;
    public static volatile boolean autoAdvance = true;
    public static volatile int profile = 0; // 0 balanced, 1 safe, 2 aggressive
    public static volatile String status = "Idle";
}
