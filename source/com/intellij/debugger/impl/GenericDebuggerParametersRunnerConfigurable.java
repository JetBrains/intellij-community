package com.intellij.debugger.impl;

import com.intellij.debugger.settings.*;
import com.intellij.debugger.ui.PropertiesDialog;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class GenericDebuggerParametersRunnerConfigurable extends SettingsEditor<GenericDebuggerRunnerSettings> {
  private JPanel myPanel;
  private JTextField myAddressField;
  private JPanel myShMemPanel;
  private JPanel myPortPanel;
  private JTextField myPortField;
  private boolean myIsLocal = false;
  private JButton myDebuggerSettings;
  private JRadioButton mySocketTransport;
  private JRadioButton myShmemTransport;
  private JPanel myTransportPanel;

  public GenericDebuggerParametersRunnerConfigurable(final Project project) {
    myDebuggerSettings.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        DebuggerConfigurable debuggerConfigurable = new DebuggerConfigurable();
        PropertiesDialog dialog = new PropertiesDialog(debuggerConfigurable, project);
        dialog.show();
        if("".equals(getPort())) {
          setPort(GenericDebuggerRunnerSettings.getDefaultPort(getTransport()));
        }
        updateUI();
      }
    });

    final ActionListener listener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateUI();
        myPanel.repaint();
      }
    };
    mySocketTransport.addActionListener(listener);
    myShmemTransport.addActionListener(listener);

    updateUI();

    myTransportPanel.setVisible(false);

    ButtonGroup group = new ButtonGroup();
    group.add(mySocketTransport);
    group.add(myShmemTransport);
  }

  private boolean isSocket() {
    return getTransport() == DebuggerSettings.SOCKET_TRANSPORT;
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
  }

  public void disposeUIResources() {
  }

  public JComponent createEditor() {
    return myPanel;
  }

  private void updateUI() {
    myPortPanel.setVisible(isSocket());
    myShMemPanel.setVisible(!isSocket());
  }

  public void disposeEditor() {
  }

  public void resetEditorFrom(GenericDebuggerRunnerSettings runnerSettings) {
    String port = runnerSettings.DEBUG_PORT;
    if (port == null) port = "";

    setIsLocal(runnerSettings.LOCAL);
    setTransport(runnerSettings.getTransport());
    setPort(port);

    updateUI();
  }

  private int getTransport() {
    if(myIsLocal) {
      return DebuggerSettings.getInstance().DEBUGGER_TRANSPORT;
    }
    else {
      return mySocketTransport.isSelected() ? DebuggerSettings.SOCKET_TRANSPORT : DebuggerSettings.SHMEM_TRANSPORT;
    }
  }

  private String getPort() {
    if (isSocket()) {
      return myPortField.getText();
    }
    else {
      return myAddressField.getText();
    }
  }

  private void setTransport(int transport) {
    mySocketTransport.setSelected(transport == DebuggerSettings.SOCKET_TRANSPORT);
    myShmemTransport.setSelected (transport != DebuggerSettings.SOCKET_TRANSPORT);
  }

  private void setIsLocal(boolean b) {
    myTransportPanel.setVisible(!b);
    myDebuggerSettings.setVisible(b);
    myIsLocal = b;
  }

  private void setPort(String port) {
    if (isSocket()) {
      myPortField.setText(port);
    }
    else {
      myAddressField.setText(port);
    }
  }

  public void applyEditorTo(GenericDebuggerRunnerSettings runnerSettings) throws ConfigurationException {
    runnerSettings.LOCAL = myIsLocal;
    runnerSettings.setTransport(getTransport());
    runnerSettings.setDebugPort(getPort());
  }
}
