package org.hanuna.gitalk.swingui;

import java.awt.*;

/**
 * @author erokhins
 */
public class ColorGenerator {
    public static Color getColor(int indexColor) {
        if (indexColor == -1) {
            throw new IllegalArgumentException("bad index Color");
        }
        int r = indexColor * 200 + 30;
        int g = indexColor * 130 + 50;
        int b = indexColor * 90 + 100;

        return new Color(r % 256, g % 256, b % 256);
    }
}
