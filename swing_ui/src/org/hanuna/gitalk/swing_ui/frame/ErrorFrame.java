package org.hanuna.gitalk.swing_ui.frame;

import org.hanuna.gitalk.swing_ui.UI_Utilities;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author erokhins
 */
public class ErrorFrame extends JFrame {
    private final JTextArea textArea = new JTextArea();

    public ErrorFrame(@NotNull String message) throws HeadlessException {
        super("fatal error");
        prepare(message);
    }

    private void prepare(@NotNull String message) {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        textArea.setText(message);
        textArea.enableInputMethods(false);
        setContentPane(textArea);
        pack();
        setModalExclusionType(Dialog.ModalExclusionType.NO_EXCLUDE);
        setMinimumSize(new Dimension(200, 50));
        pack();

        UI_Utilities.setCenterLocation(this);
    }

    public void setMessage(@NotNull String message) {
        textArea.setText(message);
    }

}
