/*
 * Class ExceptionBreakpointPropertiesPanel
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ide.util.TreeClassChooserDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ExceptionBreakpointPropertiesPanel extends BreakpointPropertiesPanel {
  private JCheckBox myNotifyCaughtCheckBox;
  private JCheckBox myNotifyUncaughtCheckBox;
  private Project myProject;
  private ExceptionBreakpoint myExceptionBreakpoint;

  public ExceptionBreakpointPropertiesPanel(Project project) {
    super(project);
    myProject = project;
  }

  protected TreeClassChooserDialog.ClassFilter createClassConditionFilter() {
    return null;
  }

  protected JComponent createSpecialBox() {
    JPanel _panel, _panel0;

    myNotifyCaughtCheckBox = new JCheckBox("Caught exception");
    myNotifyCaughtCheckBox.setMnemonic('N');
    myNotifyUncaughtCheckBox = new JCheckBox("Uncaught exception");
    myNotifyUncaughtCheckBox.setMnemonic('o');

    Box notificationsBox = Box.createVerticalBox();
    _panel = new JPanel(new BorderLayout());
    _panel.add(myNotifyCaughtCheckBox, BorderLayout.NORTH);
    notificationsBox.add(_panel);
    _panel = new JPanel(new BorderLayout());
    _panel.add(myNotifyUncaughtCheckBox, BorderLayout.NORTH);
    notificationsBox.add(_panel);

    _panel = new JPanel(new BorderLayout());
    _panel0 = new JPanel(new BorderLayout());
    _panel0.add(notificationsBox, BorderLayout.CENTER);
    _panel0.add(Box.createHorizontalStrut(3), BorderLayout.WEST);
    _panel0.add(Box.createHorizontalStrut(3), BorderLayout.EAST);
    _panel.add(_panel0, BorderLayout.NORTH);
    _panel.setBorder(IdeBorderFactory.createTitledBorder("Notifications"));

    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JCheckBox toCheck = null;
        if (!myNotifyCaughtCheckBox.isSelected() && !myNotifyUncaughtCheckBox.isSelected()) {
          Object source = e.getSource();
          if (myNotifyCaughtCheckBox.equals(source)) {
            toCheck = myNotifyUncaughtCheckBox;
          }
          else if (myNotifyUncaughtCheckBox.equals(source)) {
            toCheck = myNotifyCaughtCheckBox;
          }
          if (toCheck != null) {
            toCheck.setSelected(true);
          }
        }
      }
    };
    myNotifyCaughtCheckBox.addActionListener(listener);
    myNotifyUncaughtCheckBox.addActionListener(listener);
    return _panel;
  }

  protected void updateCheckboxes() {
    super.updateCheckboxes();
    myPassCountCheckbox.setEnabled(!(myExceptionBreakpoint instanceof AnyExceptionBreakpoint));
  }

  public void initFrom(Breakpoint breakpoint) {
    ExceptionBreakpoint exceptionBreakpoint = (ExceptionBreakpoint)breakpoint;
    myExceptionBreakpoint = exceptionBreakpoint;
    super.initFrom(breakpoint);

    myNotifyCaughtCheckBox.setSelected(exceptionBreakpoint.NOTIFY_CAUGHT);
    myNotifyUncaughtCheckBox.setSelected(exceptionBreakpoint.NOTIFY_UNCAUGHT);
  }

  public void saveTo(Breakpoint breakpoint, Runnable afterUpdate) {
    ExceptionBreakpoint exceptionBreakpoint = (ExceptionBreakpoint)breakpoint;
    exceptionBreakpoint.NOTIFY_CAUGHT = myNotifyCaughtCheckBox.isSelected();
    exceptionBreakpoint.NOTIFY_UNCAUGHT = myNotifyUncaughtCheckBox.isSelected();

    super.saveTo(breakpoint, afterUpdate);
  }
}