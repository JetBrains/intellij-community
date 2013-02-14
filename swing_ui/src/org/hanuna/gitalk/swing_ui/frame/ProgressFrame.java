package org.hanuna.gitalk.swing_ui.frame;

import org.hanuna.gitalk.swing_ui.UI_Utilities;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author erokhins
 */
public class ProgressFrame extends JFrame {
    private final static int MAX_PROGRESS_VALUE = 1000;
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel label = new JLabel();


    public ProgressFrame(@NotNull String startMessage) {
        label.setText(startMessage);
        packElements();
    }

    private void packElements() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Progress");
        progressBar.setMinimum(0);
        progressBar.setMaximum(MAX_PROGRESS_VALUE);
        progressBar.setIndeterminate(true);
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(2, 1));

        panel.add(progressBar);
        panel.add(label);
        setContentPane(panel);
        setMinimumSize(new Dimension(250, 70));

        pack();
        UI_Utilities.setCenterLocation(this);
    }

    public void setMessage(@NotNull String message) {
        label.setText(message);
    }

}
