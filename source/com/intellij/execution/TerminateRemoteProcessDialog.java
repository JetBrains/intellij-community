/**
 * created at Dec 14, 2001
 * @author Jeka
 */
package com.intellij.execution;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class TerminateRemoteProcessDialog extends DialogWrapper {
  private JCheckBox myTerminateCheckBox;
  private final String mySessionName;
  private final boolean myDetachIsDefault;

  public TerminateRemoteProcessDialog(final Project project, final String configurationName, final boolean detachIsDefault) {
    super(project, true);
    mySessionName = configurationName;
    myDetachIsDefault = detachIsDefault;
    setTitle("Process \"" + mySessionName + "\" is running");
    setOKButtonText("Disconnect");
    setButtonsAlignment(SwingUtilities.CENTER);
    this.init();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(), getCancelAction()};
  }

  protected JComponent createNorthPanel() {
    final String message;
    message = "Disconnect from the process \"" + mySessionName + "\"?";
    final JLabel label = new JLabel(message);
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(label, BorderLayout.CENTER);
    final Icon icon = UIManager.getIcon("OptionPane.warningIcon");
    if (icon != null) {
      label.setIcon(icon);
      label.setIconTextGap(7);
    }
    return panel;
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    myTerminateCheckBox = new JCheckBox("Terminate the process after disconnect");
    myTerminateCheckBox.setMnemonic(KeyEvent.VK_T);
    myTerminateCheckBox.setSelected(!myDetachIsDefault);
    panel.add(myTerminateCheckBox, BorderLayout.EAST);
    return panel;
  }

  public boolean forceTermination() {
    return myTerminateCheckBox.isSelected();
  }
}
