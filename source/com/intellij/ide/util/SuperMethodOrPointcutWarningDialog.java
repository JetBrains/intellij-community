
package com.intellij.ide.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

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
    setTitle("Warning");
    setButtonsAlignment(SwingUtilities.CENTER);
    setOKButtonText("&Yes");
    init();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),new NoAction(),getCancelAction()};
  }

  public JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    Icon icon = UIManager.getIcon("OptionPane.warningIcon");
    if (icon != null){
      JLabel iconLabel = new JLabel(UIManager.getIcon("OptionPane.questionIcon"));
      panel.add(iconLabel, BorderLayout.WEST);
    }
    JPanel labelsPanel = new JPanel(new GridLayout(3, 1, 0, 0));
    labelsPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 10));
    String classType = myIsContainedInAspect ? "aspect" : (myIsParentInterface ? "interface" : "class");
    String methodOrPointcut = myIsPointcut ? "pointcut" : "method";
    labelsPanel.add(new JLabel(capitalize(methodOrPointcut + " " + myName)));
    if (myIsContainedInInterface || !myIsSuperAbstract){
      labelsPanel.add(new JLabel("overrides " + methodOrPointcut + " of " + classType + " " + myClassName + "."));
    }
    else{
      labelsPanel.add(new JLabel("implements " + methodOrPointcut + " of " + classType + " " + myClassName + "."));
    }
    String s = "Do you want to " + myActionString + " the " + methodOrPointcut + " from " + (myIsParentInterface ? "" : "base ") + classType + "?";
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
      super("&No");
    }

    public void actionPerformed(ActionEvent e) {
      close(NO_EXIT_CODE);
    }
  }
}

