
package com.intellij.ide.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ide.IdeBundle;
import com.intellij.CommonBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

//TODO: review title and text!!!
class SuperMethodOrPointcutWarningDialog extends DialogWrapper {
  public static final int NO_EXIT_CODE=NEXT_USER_EXIT_CODE+1;
  private String myName;
  private boolean myIsPointcut;
  private String myClassName;
  private String myActionString;
  private boolean myIsSuperAbstract;
  private boolean myIsParentInterface;
  private boolean myIsContainedInInterface;
  private boolean myIsContainedInAspect;

  public SuperMethodOrPointcutWarningDialog(Project project, String name, boolean isPointcut, String className, String actionString, boolean isSuperAbstract, boolean isParentInterface, boolean isContainedInInterface, boolean isContainedInAspect) {
    super(project, true);
    myName = name;
    myIsPointcut = isPointcut;
    myClassName = className;
    myActionString = actionString;
    myIsSuperAbstract = isSuperAbstract;
    myIsParentInterface = isParentInterface;
    myIsContainedInInterface = isContainedInInterface;
    myIsContainedInAspect = isContainedInAspect;
    setTitle(IdeBundle.message("title.warning"));
    setButtonsAlignment(SwingUtilities.CENTER);
    setOKButtonText(CommonBundle.getYesButtonText());
    init();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),new NoAction(),getCancelAction()};
  }

  public JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    Icon icon = Messages.getWarningIcon();
    if (icon != null){
      JLabel iconLabel = new JLabel(Messages.getQuestionIcon());
      panel.add(iconLabel, BorderLayout.WEST);
    }
    JPanel labelsPanel = new JPanel(new GridLayout(3, 1, 0, 0));
    labelsPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 10));
    String classType = myIsContainedInAspect
                       ? IdeBundle.message("element.of.aspect")
                       : (myIsParentInterface ? IdeBundle.message("element.of.interface") : IdeBundle.message("element.of.class"));
    String methodOrPointcut = myIsPointcut ? IdeBundle.message("element.pointcut") : IdeBundle.message("element.method");
    labelsPanel.add(new JLabel(myIsPointcut ? IdeBundle.message("label.pointcut", myName) : IdeBundle.message("label.method", myName)));
    if (myIsContainedInInterface || !myIsSuperAbstract){
      labelsPanel.add(new JLabel(IdeBundle.message("label.overrides.method_or_pointcut.of_class_or_interface.name", methodOrPointcut, classType, myClassName)));
    }
    else{
      labelsPanel.add(new JLabel(IdeBundle.message("label.implements.method_or_pointcut.of_class_or_interface.name", methodOrPointcut, classType, myClassName)));
    }
    String fromClassType = myIsContainedInAspect
                       ? IdeBundle.message("element.from.base.aspect")
                       : (myIsParentInterface ? IdeBundle.message("element.from.interface") : IdeBundle.message("element.from.base.class"));
    String s = IdeBundle.message("prompt.do.you.want.to.action_verb.the.method_or_pointcut.from_class", myActionString, methodOrPointcut, fromClassType);
    labelsPanel.add(new JLabel(s));
    panel.add(labelsPanel, BorderLayout.CENTER);
    return panel;
  }

  public static String capitalize(String text) {
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  public JComponent createCenterPanel() {
    return null;
  }

  private class NoAction extends AbstractAction {
    public NoAction() {
      super(CommonBundle.getNoButtonText());
    }

    public void actionPerformed(ActionEvent e) {
      close(NO_EXIT_CODE);
    }
  }
}

