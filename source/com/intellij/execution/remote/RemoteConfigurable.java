/*
 * Class RemoteConfigurable
 * @author Jeka
 */
package com.intellij.execution.remote;

import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.DocumentAdapter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.*;

public class RemoteConfigurable extends SettingsEditor<RemoteConfiguration> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.remote.RemoteConfigurable");
  JPanel myPanel;
  private JRadioButton myRbSocket;
  private JRadioButton myRbShmem;
  private JRadioButton myRbListen;
  private JRadioButton myRbAttach;
  private JTextField myAddressField;
  private JTextField myHostField;
  private JTextField myPortField;
  private JPanel myShmemPanel;
  private JPanel mySocketPanel;
  private RemoteConfigurationArguments myHelpArea;
  private String myHostName = "";

  public RemoteConfigurable() {
    final ButtonGroup transportGroup = new ButtonGroup();
    transportGroup.add(myRbSocket);
    transportGroup.add(myRbShmem);

    final ButtonGroup connectionGroup = new ButtonGroup();
    connectionGroup.add(myRbListen);
    connectionGroup.add(myRbAttach);

    final DocumentListener helpTextUpdater = new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        updateHelpText();
      }
    };
    myAddressField.getDocument().addDocumentListener(helpTextUpdater);
    myHostField.getDocument().addDocumentListener(helpTextUpdater);
    myPortField.getDocument().addDocumentListener(helpTextUpdater);
    myRbSocket.setSelected(true);
    final ActionListener listener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final Object source = e.getSource();
        if (source.equals(myRbSocket)) {
           myShmemPanel.setVisible(false);
           mySocketPanel.setVisible(true);
        }
        else if (source.equals(myRbShmem)) {
           myShmemPanel.setVisible(true);
           mySocketPanel.setVisible(false);
        }
        myPanel.repaint();
        updateHelpText();
      }
    };
    myRbShmem.addActionListener(listener);
    myRbSocket.addActionListener(listener);

    final ItemListener updateListener = new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        final boolean isAttach = myRbAttach.isSelected();

        if(!isAttach && myHostField.isEditable()) {
          myHostName = myHostField.getText();
        }

        myHostField.setEditable(isAttach);
        myHostField.setEnabled(isAttach);

        myHostField.setText(isAttach ? myHostName : "localhost");
        updateHelpText();
      }
    };
    myRbAttach.addItemListener(updateListener);
    myRbListen.addItemListener(updateListener);

    final FocusListener fieldFocusListener = new FocusAdapter() {
      public void focusLost(final FocusEvent e) {
        updateHelpText();
      }
    };
    myAddressField.addFocusListener(fieldFocusListener);
    myPortField.addFocusListener(fieldFocusListener);
  }

  public void applyEditorTo(final RemoteConfiguration configuration) {
    LOG.assertTrue(configuration != null);
    final RemoteConfiguration remoteConfiguration = configuration;
    remoteConfiguration.HOST = (myHostField.isEditable() ? myHostField.getText() : myHostName).trim();
    if ("".equals(remoteConfiguration.HOST)) {
      remoteConfiguration.HOST = null;
    }
    remoteConfiguration.PORT = myPortField.getText().trim();
    if ("".equals(remoteConfiguration.PORT)) {
      remoteConfiguration.PORT = null;
    }
    remoteConfiguration.SHMEM_ADDRESS = myAddressField.getText().trim();
    if ("".equals(remoteConfiguration.SHMEM_ADDRESS)) {
      remoteConfiguration.SHMEM_ADDRESS = null;
    }
    remoteConfiguration.USE_SOCKET_TRANSPORT = myRbSocket.isSelected();
    remoteConfiguration.SERVER_MODE = myRbListen.isSelected();
  }

  public void resetEditorFrom(final RemoteConfiguration configuration) {
    final RemoteConfiguration remoteConfiguration = configuration;
    if (!SystemInfo.isWindows) {
      remoteConfiguration.USE_SOCKET_TRANSPORT = true;
      myRbShmem.setEnabled(false);
      myAddressField.setEditable(false);
    }
    myAddressField.setText(remoteConfiguration.SHMEM_ADDRESS);
    myHostName = remoteConfiguration.HOST;
    myHostField.setText(remoteConfiguration.HOST);
    myPortField.setText(remoteConfiguration.PORT);
    if (remoteConfiguration.USE_SOCKET_TRANSPORT) {
      myRbSocket.doClick();
    }
    else {
      myRbShmem.doClick();
    }
    if (remoteConfiguration.SERVER_MODE) {
      myRbListen.doClick();
    }
    else {
      myRbAttach.doClick();
    }
    myRbShmem.setEnabled(SystemInfo.isWindows);
  }

  public JComponent createEditor() {
    return myPanel;
  }

  public void disposeEditor() {
  }

  private void updateHelpText() {
    boolean useSockets = !myRbShmem.isSelected();

    myHelpArea.updateText(new RemoteConnection(
      useSockets,
      myHostName,
      useSockets ? myPortField.getText().trim() : myAddressField.getText().trim(),
      myRbListen.isSelected()
    ));
  }


  public String getHelpTopic() {
    return "project.runDebugRemote";
  }
}