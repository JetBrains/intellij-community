package org.hanuna.gitalk.swing_ui;

import java.awt.*;

/**
 * @author erokhins
 */
public class UI_Utilities {

    public static void setCenterLocation(Window frame) {
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension frameDimension = frame.getSize();
        int x = screenDimension.width / 2 - frameDimension.width / 2;
        int y = screenDimension.height / 2 - frameDimension.height / 2;
        frame.setLocation(x, y);
    }

}
