package org.hanuna.gitalk.swing_ui.render.painters;

import org.hanuna.gitalk.graph.graph_elements.Branch;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author erokhins
 */
public class ColorGenerator {
    private static final Map<Integer, Color> colorMap = new HashMap<Integer, Color>();

    @NotNull
    public static Color getColor(@NotNull Branch branch) {
        int indexColor = branch.getNumberOfBranch();
        Color color = colorMap.get(indexColor);
        if (color == null) {
            color = getColor(indexColor);
            colorMap.put(indexColor, color);
        }
        return color;
    }

    private static Color getColor(int indexColor) {
        if (indexColor == -1) {
            throw new IllegalArgumentException("bad index Color");
        }
        int r = indexColor * 200 + 30;
        int g = indexColor * 130 + 50;
        int b = indexColor * 90 + 100;

        return new Color(r % 256, g % 256, b % 256);
    }
}
