package com.intellij.util.net;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Oct 7, 2003
 * Time: 3:56:25 PM
 * To change this template use Options | File Templates.
 */
public class AuthenticationDialog extends JDialog {
  private AuthenticationPanel panel;

  public AuthenticationDialog(String title, String description) {
    super(JOptionPane.getRootFrame(), title, true);

    panel = new AuthenticationPanel(description,
                                    HttpConfigurable.getInstance().PROXY_LOGIN,
                                    HttpConfigurable.getInstance().getPlainProxyPassword(),
                                    HttpConfigurable.getInstance().KEEP_PROXY_PASSWORD);

    getContentPane().setLayout(new BorderLayout ());
    getContentPane().add(panel, BorderLayout.CENTER);

    JPanel buttonPanel = new JPanel ();
    buttonPanel.setLayout(new GridLayout (1, 2));
    for (int i = 0; i < createActions().length; i++) {
      Action action = createActions()[i];
      buttonPanel.add(new JButton (action), i);
    }

    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

    Dimension parentSize = Toolkit.getDefaultToolkit().getScreenSize();
    Dimension ownSize = getPreferredSize();

    setLocation((parentSize.width - ownSize.width) / 2, (parentSize.height - ownSize.height) / 2);

    pack();
  }

  protected Action[] createActions() {
    Action [] actions =
      new Action [] {
        new AbstractAction ("OK") {
          public void actionPerformed(ActionEvent e) {
            HttpConfigurable.getInstance().PROXY_LOGIN = panel.getLogin();
            HttpConfigurable.getInstance().setPlainProxyPassword(panel.getPassword());
            HttpConfigurable.getInstance().PROXY_AUTHENTICATION = true;
            HttpConfigurable.getInstance().KEEP_PROXY_PASSWORD = panel.isRememberPassword();

            dispose();
          }
        },
        new AbstractAction("Cancel") {
          public void actionPerformed(ActionEvent e) {
            HttpConfigurable.getInstance().PROXY_AUTHENTICATION = false;
            dispose();
          }
        }
      };
    actions [0].putValue(Action.DEFAULT, "true");
    return actions;
  }
}
