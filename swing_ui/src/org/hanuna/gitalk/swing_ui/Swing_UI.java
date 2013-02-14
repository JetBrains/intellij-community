package org.hanuna.gitalk.swing_ui;

import org.hanuna.gitalk.swing_ui.frame.ErrorFrame;
import org.hanuna.gitalk.swing_ui.frame.MainFrame;
import org.hanuna.gitalk.swing_ui.frame.ProgressFrame;
import org.hanuna.gitalk.ui.ControllerListener;
import org.hanuna.gitalk.ui.UI_Controller;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author erokhins
 */
public class Swing_UI {
    private final ErrorFrame errorFrame = new ErrorFrame("error");
    private final ProgressFrame progressFrame = new ProgressFrame("Begin load");
    private final ControllerListener swingControllerListener = new SwingControllerListener();
    private final UI_Controller ui_controller;
    private MainFrame mainFrame = null;

    public Swing_UI(UI_Controller ui_controller) {
        this.ui_controller = ui_controller;
    }


    public ControllerListener getControllerListener() {
        return swingControllerListener;
    }

    public void setState(ControllerListener.State state) {
        switch (state) {
            case USUAL:
                if (mainFrame == null) {
                    mainFrame = new MainFrame(ui_controller);
                }
                mainFrame.setVisible(true);
                errorFrame.setVisible(false);
                progressFrame.setVisible(false);
                break;
            case ERROR:
                errorFrame.setVisible(true);
                break;
            case PROGRESS:
                progressFrame.setVisible(true);
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private class SwingControllerListener implements ControllerListener {

        @Override
        public void jumpToRow(int rowIndex) {
            mainFrame.getGraphTable().jumpToRow(rowIndex);
        }

        @Override
        public void updateUI() {
            mainFrame.getGraphTable().updateUI();
        }

        @Override
        public void setState(@NotNull final State state) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    Swing_UI.this.setState(state);
                }
            });
        }

        @Override
        public void setErrorMessage(@NotNull String errorMessage) {
            errorFrame.setMessage(errorMessage);
        }

        @Override
        public void setUpdateProgressMessage(@NotNull String progressMessage) {
            progressFrame.setMessage(progressMessage);
        }
    }
}
