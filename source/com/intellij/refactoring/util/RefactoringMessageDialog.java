package com.intellij.refactoring.util;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class RefactoringMessageDialog extends DialogWrapper{
  private String myMessage;
  private String myHelpTopic;
  private Icon myIcon;
  private boolean myIsCancelButtonVisible;

  public RefactoringMessageDialog(String title, String message, String helpTopic, String iconId, boolean showCancelButton, Project project) {
    super(project, false);
    constructor(title, message, helpTopic, showCancelButton, iconId);
  }

  private void constructor(String title, String message, String helpTopic, boolean isCancelButtonVisible, String iconId) {
    setTitle(title);
    myMessage = message;
    myHelpTopic = helpTopic;
    myIsCancelButtonVisible=isCancelButtonVisible;
    setButtonsAlignment(SwingUtilities.CENTER);
    myIcon = UIManager.getIcon(iconId);
    init();
  }

  protected Action[] createActions(){
    ArrayList actions=new ArrayList();
    actions.add(getOKAction());
    if(myIsCancelButtonVisible){
      actions.add(getCancelAction());
    }
    if(myHelpTopic!=null){
      actions.add(getHelpAction());
    }
    return (Action[])actions.toArray(new Action[actions.size()]);
  }

  protected JComponent createNorthPanel() {
    JLabel label = new JLabel(myMessage);
    label.setUI(new MultiLineLabelUI());
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(label, BorderLayout.CENTER);
    if (myIcon != null) {
      label.setIcon(myIcon);
      label.setIconTextGap(10);
    }
    panel.add(Box.createVerticalStrut(7), BorderLayout.SOUTH);
    return panel;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpTopic);
  }
}