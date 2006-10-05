
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

//TODO: review title and text!!!
class SuperMethodWarningDialog extends DialogWrapper {
  public static final int NO_EXIT_CODE=NEXT_USER_EXIT_CODE+1;
  private String myName;
  private String[] myClassNames;
  private String myActionString;
  private boolean myIsSuperAbstract;
  private boolean myIsParentInterface;
  private boolean myIsContainedInInterface;

  public SuperMethodWarningDialog(Project project,
                                  String name,
                                  String actionString,
                                  boolean isSuperAbstract,
                                  boolean isParentInterface,
                                  boolean isContainedInInterface,
                                  String... classNames) {
    super(project, true);
    myName = name;
    myClassNames = classNames;
    myActionString = actionString;
    myIsSuperAbstract = isSuperAbstract;
    myIsParentInterface = isParentInterface;
    myIsContainedInInterface = isContainedInInterface;
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
    JPanel labelsPanel = new JPanel(new GridLayout(0, 1, 0, 0));
    labelsPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 10));
    String classType = myIsParentInterface ? IdeBundle.message("element.of.interface") : IdeBundle.message("element.of.class");
    String methodString = IdeBundle.message("element.method");
    labelsPanel.add(new JLabel(IdeBundle.message("label.method", myName)));
    if (myClassNames.length == 1) {
      final String className = myClassNames[0];
      labelsPanel.add(new JLabel(myIsContainedInInterface || !myIsSuperAbstract
                                 ? IdeBundle.message("label.overrides.method.of_class_or_interface.name", methodString, classType, className)
                                 : IdeBundle.message("label.implements.method.of_class_or_interface.name", methodString, classType, className)));
    } else {
      labelsPanel.add(new JLabel(IdeBundle.message("label.implements.method.of_interfaces")));
      for (final String className : myClassNames) {
        labelsPanel.add(new JLabel("    " + className));
      }
    }
    labelsPanel.add(new JLabel(IdeBundle.message("prompt.do.you.want.to.action_verb.the.method.from_class", myActionString, myClassNames.length > 1 ? 2 : 1)));
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

