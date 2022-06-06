package com.example.vnolib.command.servercommands;

public enum MessageColor {
    WHITE(0),
    BLUE(1),
    PINK(2),
    YELLOW(3),
    GREEN(4),
    ORANGE(5),
    RED(6);

    public final int colorIndex;

    MessageColor(int index) {
        colorIndex = index;
    }
}