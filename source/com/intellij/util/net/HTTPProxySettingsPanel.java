package com.intellij.util.net;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Aug 28, 2003
 * Time: 3:52:47 PM
 * To change this template use Options | File Templates.
 */
public class HTTPProxySettingsPanel extends JPanel {
  private JPanel myMainPanel;
  private JPanel myInternalPanel;

  private JTextField myProxyLoginTextField;
  private JPasswordField myProxyPasswordTextField;
  private JCheckBox myProxyAuthCheckBox;
  private JTextField myProxyPortTextField;
  private JTextField myProxyHostTextField;
  private JCheckBox myUseProxyCheckBox;
  private JCheckBox myRememberProxyPasswordCheckBox;

  private JLabel myProxyLoginLabel;
  private JLabel myProxyPasswordLabel;
  private JLabel myHostNameLabel;
  private JLabel myPortNumberLabel;

  private boolean myModified = false;
  private JPanel myAdditionalPanel;

  public boolean isModified() {
    return myModified;
  }

  private class DocumentModifyListener implements DocumentListener {
      public void removeUpdate(DocumentEvent e) {
        myModified = true;
      }

      public void changedUpdate(DocumentEvent e) {
        myModified = true;
      }

      public void insertUpdate(DocumentEvent e) {
        myModified = true;
      }
  }

  public HTTPProxySettingsPanel() {
    add(myMainPanel);

    myProxyAuthCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myModified = true;
        enableProxyAuthentication(myProxyAuthCheckBox.isSelected());
      }
    });

    myUseProxyCheckBox.addActionListener(new ActionListener () {
      public void actionPerformed(ActionEvent e) {
        myModified = true;
        enableProxy(myUseProxyCheckBox.isSelected());
      }
    });

    myRememberProxyPasswordCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myModified = true;
      }
    });

    myProxyLoginTextField.getDocument().addDocumentListener(new DocumentModifyListener());
    myProxyPasswordTextField.getDocument().addDocumentListener(new DocumentModifyListener());
    myProxyPortTextField.getDocument().addDocumentListener(new DocumentModifyListener());
    myProxyHostTextField.getDocument().addDocumentListener(new DocumentModifyListener());

    myUseProxyCheckBox.setSelected(HttpConfigurable.getInstance().USE_HTTP_PROXY);
    myProxyAuthCheckBox.setSelected(HttpConfigurable.getInstance().PROXY_AUTHENTICATION);

    enableProxy(HttpConfigurable.getInstance().USE_HTTP_PROXY);

    myProxyLoginTextField.setText(HttpConfigurable.getInstance().PROXY_LOGIN);
    myProxyPasswordTextField.setText(HttpConfigurable.getInstance().getPlainProxyPassword());

    myProxyPortTextField.setText(Integer.toString(HttpConfigurable.getInstance().PROXY_PORT));
    myProxyHostTextField.setText(HttpConfigurable.getInstance().PROXY_HOST);

    myRememberProxyPasswordCheckBox.setSelected(HttpConfigurable.getInstance().KEEP_PROXY_PASSWORD);
  }

  public void apply () {
    HttpConfigurable.getInstance().USE_HTTP_PROXY = myUseProxyCheckBox.isSelected();
    HttpConfigurable.getInstance().PROXY_AUTHENTICATION = myProxyAuthCheckBox.isSelected();
    HttpConfigurable.getInstance().KEEP_PROXY_PASSWORD = myRememberProxyPasswordCheckBox.isSelected();

    HttpConfigurable.getInstance().PROXY_LOGIN = myProxyLoginTextField.getText();
    HttpConfigurable.getInstance().setPlainProxyPassword(new String (myProxyPasswordTextField.getPassword()));

    try {
      HttpConfigurable.getInstance().PROXY_PORT = Integer.valueOf(myProxyPortTextField.getText()).intValue();
    } catch (NumberFormatException e) {
      HttpConfigurable.getInstance().PROXY_PORT = 80;
    }
    HttpConfigurable.getInstance().PROXY_HOST = myProxyHostTextField.getText();

    myModified = false;
  }

  private void enableProxy (boolean enabled) {
    myHostNameLabel.setEnabled(enabled);
    myPortNumberLabel.setEnabled(enabled);
    myProxyHostTextField.setEnabled(enabled);
    myProxyPortTextField.setEnabled(enabled);

    myProxyAuthCheckBox.setEnabled(enabled);
    enableProxyAuthentication(enabled && myProxyAuthCheckBox.isSelected());
  }

  private void enableProxyAuthentication (boolean enabled) {
    myProxyPasswordLabel.setEnabled(enabled);
    myProxyLoginLabel.setEnabled(enabled);

    myProxyLoginTextField.setEnabled(enabled);
    myProxyPasswordTextField.setEnabled(enabled);

    myRememberProxyPasswordCheckBox.setEnabled(enabled);
  }
}
