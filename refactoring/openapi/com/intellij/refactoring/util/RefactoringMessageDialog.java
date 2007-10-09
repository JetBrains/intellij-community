/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.refactoring.util;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

import org.jetbrains.annotations.NonNls;

public class RefactoringMessageDialog extends DialogWrapper{
  private String myMessage;
  private String myHelpTopic;
  private Icon myIcon;
  private boolean myIsCancelButtonVisible;

  public RefactoringMessageDialog(String title, String message, String helpTopic, @NonNls String iconId, boolean showCancelButton, Project project) {
    super(project, false);
    setTitle(title);
    myMessage = message;
    myHelpTopic = helpTopic;
    myIsCancelButtonVisible =showCancelButton;
    setButtonsAlignment(SwingUtilities.CENTER);
    myIcon = UIManager.getIcon(iconId);
    init();
  }

  protected Action[] createActions(){
    ArrayList<Action> actions=new ArrayList<Action>();
    actions.add(getOKAction());
    if(myIsCancelButtonVisible){
      actions.add(getCancelAction());
    }
    if(myHelpTopic!=null){
      actions.add(getHelpAction());
    }
    return actions.toArray(new Action[actions.size()]);
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