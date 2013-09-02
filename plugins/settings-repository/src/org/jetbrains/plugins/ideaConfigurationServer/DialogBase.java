package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.MnemonicHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Nov 12, 2008
 * Time: 6:07:24 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class DialogBase extends JDialog {
  protected static final int CANCEL_INTERVAL = 60;

  protected DialogBase(Frame frame, String title) throws java.awt.HeadlessException {
    super(frame, title, true);
  }

  protected DialogBase(String title) {
    super((Dialog)null, title, true);
  }

  protected void init() {
    new MnemonicHelper().register(getContentPane());

    getContentPane().add(getCenterPanel());

    setModal(true);
    getRootPane().setDefaultButton(getDefaultButton());

    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        setVisible(false);
      }
    };
    getRootPane().registerKeyboardAction(actionListener, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

    pack();
    setLocationRelativeTo(null);
  }

  protected abstract JButton getDefaultButton();

  protected abstract JPanel getCenterPanel();
}
