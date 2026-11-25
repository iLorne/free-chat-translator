package com.chattranslator;

import java.awt.Rectangle;

/** Horizontal widget geometry gate; Y grouping remains in ChatTranslatorPlugin. */
public final class ChatRowHitTest
{
    private ChatRowHitTest() { }

    public static boolean containsHorizontal(Rectangle bounds, int x)
    {
        return bounds != null && bounds.width > 0 && x >= bounds.x && (long) x < (long) bounds.x + bounds.width;
    }
}
