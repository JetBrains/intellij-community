package org.hanuna.gitalk.swing_ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author erokhins
 */
public class ErrorFrame extends JFrame {

    public ErrorFrame(@NotNull String message) throws HeadlessException {
        super("fatal error");
        prepare(message);
        setVisible(true);
    }

    private void prepare(@NotNull String message) {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JTextArea textArea = new JTextArea(message);
        setContentPane(textArea);
        pack();
        setModalExclusionType(Dialog.ModalExclusionType.NO_EXCLUDE);
        setMinimumSize(new Dimension(200, 50));
        pack();

        UI_Utilities.setCenterLocation(this);
    }

}
