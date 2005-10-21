/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.wizard;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.CommandButtonGroup;
import com.intellij.ui.SeparatorComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public class WizardDialog extends DialogWrapper implements WizardCallback {

  private final WizardModel myModel;

  private JButton myPrevious = new JButton();
  private JButton myNext = new JButton();
  private JButton myFinish = new JButton();
  private JButton myCancel = new JButton();
  private JButton myHelp = new JButton();

  private JLabel myIcon = new JLabel();
  private JLabel myHeader = new JLabel();
  private JLabel myExplanation = new JLabel();

  private JPanel myStepContent;

  public WizardDialog(Project project, boolean canBeParent, WizardModel model) {
    super(project, canBeParent);
    myModel = model;
    init();
  }

  public WizardDialog(boolean canBeParent, WizardModel model) {
    super(canBeParent);
    myModel = model;
    init();
  }

  public WizardDialog(Component parent, boolean canBeParent, WizardModel model) {
    super(parent, canBeParent);
    myModel = model;
    init();
  }


  protected JComponent createCenterPanel() {
    JPanel result = new JPanel(new BorderLayout());

    JPanel icon = new JPanel(new BorderLayout());
    icon.add(myIcon, BorderLayout.NORTH);
    result.add(icon, BorderLayout.WEST);

    JPanel header = new JPanel();
    header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
    header.add(myHeader);
    header.add(Box.createVerticalStrut(4));
    header.add(myExplanation);
    header.add(Box.createVerticalStrut(4));
    header.add(new SeparatorComponent(0, Color.gray, null));
    header.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));

    JPanel content = new JPanel(new BorderLayout(12, 12));
    content.add(header, BorderLayout.NORTH);

    myStepContent = new JPanel(new BorderLayout()) {
      public Dimension getPreferredSize() {
        Dimension custom = getWindowPreferredSize();
        Dimension superSize = super.getPreferredSize();
        if (custom != null) {
          custom.width = custom.width > 0 ? custom.width : superSize.width;
          custom.height = custom.height > 0 ? custom.height : superSize.height;
        } else {
          custom = superSize;
        }
        return custom;
      }
    };

    content.add(header, BorderLayout.NORTH);
    content.add(myStepContent, BorderLayout.CENTER);

    result.add(content, BorderLayout.CENTER);

    myHeader.setFont(myHeader.getFont().deriveFont(Font.BOLD, 16));
    myHeader.setFont(myHeader.getFont().deriveFont(Font.PLAIN, 14));

    return result;
  }

  protected void init() {
    setTitle(myModel.getTitle());

    initHelpButton();

    myModel.setCallback(this);
    super.init();

    initCurrentStep();
  }

  private void initHelpButton() {
    myHelp.setText("Help");
    myHelp.setMnemonic('H');
    myHelp.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onHelp();
      }
    });

    getRootPane().registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          onHelp();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
      JComponent.WHEN_IN_FOCUSED_WINDOW
    );

    getRootPane().registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          onHelp();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0),
      JComponent.WHEN_IN_FOCUSED_WINDOW
    );
  }

  private void onHelp() {
    HelpManager.getInstance().invokeHelp(myModel.getCurrentStep().getHelpId());
  }

  private void initCurrentStep() {
    WizardStep current = myModel.getCurrentStep();

    myIcon.setIcon(current.getIcon());
    myHeader.setFont(myHeader.getFont().deriveFont(Font.BOLD, 16));
    myHeader.setText(current.getTitle());
    myExplanation.setText(current.getExplanation());

    myStepContent.removeAll();
    myStepContent.add(myModel.getCurrentComponent());

    WizardNavigationState state = myModel.getCurrentNavigationState();
    myPrevious.setAction(state.PREVIOUS);
    myNext.setAction(state.NEXT);
    myFinish.setAction(state.FINISH);
    myCancel.setAction(state.CANCEL);

    if (myNext.isEnabled()) {
      getRootPane().setDefaultButton(myNext);
    } else if (myFinish.isEnabled()) {
      getRootPane().setDefaultButton(myFinish);
    } else if (myCancel.isEnabled()) {
      getRootPane().setDefaultButton(myCancel);
    } else {
      getRootPane().setDefaultButton(null);
    }
  }


  protected JComponent createSouthPanel() {
    final CommandButtonGroup panel = new CommandButtonGroup(BoxLayout.X_AXIS);
    panel.add(myPrevious);
    panel.add(myNext);
    panel.add(myFinish);
    panel.add(myCancel);
    panel.add(myHelp);
    panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    return panel;
  }

  public void onStepChanged() {
    initCurrentStep();
  }

  public void onWizardGoalDropped() {
    doCancelAction();
  }

  public void onWizardGoalAchieved() {
    doOKAction();
  }

  public boolean isWizardGoalAchieved() {
    return isOK();
  }

  protected Dimension getWindowPreferredSize() {
    return null;
  }

}