package com.intellij.util.net;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Nov 19, 2003
 * Time: 10:04:14 PM
 * To change this template use Options | File Templates.
 */
public class IOExceptionDialog extends JDialog {
  private JPanel mainPanel;
  private JButton cancelButton;
  private JButton tryAgainButton;
  private JButton setupButton;
  private JTextArea errorTextArea;
  private JLabel errorLabel;
  private boolean cancelPressed = false;

  public IOExceptionDialog(IOException e, String title, String errorText)  {
    super (JOptionPane.getRootFrame(), title, true);

    getContentPane().add(mainPanel);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintWriter writer = new PrintWriter (baos);
    e.printStackTrace(writer);
    writer.flush();
    errorTextArea.setText(baos.toString());
    errorTextArea.setCaretPosition(0);
    errorLabel.setText(errorText);

    setupButton.addActionListener(new ActionListener () {
      public void actionPerformed(ActionEvent e) {
        HTTPProxySettingsDialog dlg = new HTTPProxySettingsDialog();
        dlg.show();
      }
    });
    tryAgainButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        cancelPressed = false;
        dispose();
      }
    });
    cancelButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        cancelPressed = true;
        dispose();
      }
    });

    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

    Dimension parentSize = Toolkit.getDefaultToolkit().getScreenSize();
    Dimension ownSize = getPreferredSize();

    setLocation((parentSize.width - ownSize.width) / 2, (parentSize.height - ownSize.height) / 2);

    pack();
  }

  /**
   * Show
   * @return <code>true</code> if "Try Again" button pressed
   * @return <code>false</code> if "Cancel" button pressed
   */
  public static boolean showErrorDialog (IOException e, String title, String text) {
    e.printStackTrace(System.err);
    
    IOExceptionDialog dlg = new IOExceptionDialog(e, title, text);
    dlg.show();
    return ! dlg.cancelPressed;
  }

  public static void main(String[] args) {
    IOExceptionDialog.showErrorDialog(new IOException("test"), "Test", "Something failed");
  }
}
