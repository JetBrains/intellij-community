package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import javax.swing.*;
import java.util.Iterator;

/**
 * @author Eugene Belyaev
 */
public class ThreadsViewConfigurable extends BaseConfigurable {
  private ThreadsViewSettings mySettings;
  private JPanel myPanel;
  private JCheckBox myShowGroupsCheckBox;
  private JCheckBox myLineNumberCheckBox;
  private JCheckBox myClassNameCheckBox;
  private JCheckBox mySourceCheckBox;
  private JCheckBox myShowSyntheticsCheckBox;
  private JCheckBox myShowCurrentThreadChechBox;

  public ThreadsViewConfigurable(ThreadsViewSettings settings) {
    mySettings = settings;
  }

  public String getDisplayName() {
    return "Customize Threads View";//"Threads View Properties";
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public Icon getIcon() {
    return null;
  }

  public void apply() {
    mySettings.SHOW_CLASS_NAME = myClassNameCheckBox.isSelected();
    mySettings.SHOW_LINE_NUMBER = myLineNumberCheckBox.isSelected();
    mySettings.SHOW_SOURCE_NAME = mySourceCheckBox.isSelected();
    mySettings.SHOW_THREAD_GROUPS = myShowGroupsCheckBox.isSelected();
    mySettings.SHOW_SYNTHETIC_FRAMES = myShowSyntheticsCheckBox.isSelected();
    mySettings.SHOW_CURRENT_THREAD = myShowCurrentThreadChechBox.isSelected();
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; i < openProjects.length; i++) {
      Project project = openProjects[i];
      for (Iterator iterator = (DebuggerManagerEx.getInstanceEx(project)).getSessions().iterator(); iterator.hasNext();) {
        ((DebuggerSession)iterator.next()).refresh();
      }
    }
  }

  public void reset() {
    myClassNameCheckBox.setSelected(mySettings.SHOW_CLASS_NAME);
    myLineNumberCheckBox.setSelected(mySettings.SHOW_LINE_NUMBER);
    mySourceCheckBox.setSelected(mySettings.SHOW_SOURCE_NAME);
    myShowGroupsCheckBox.setSelected(mySettings.SHOW_THREAD_GROUPS);
    myShowSyntheticsCheckBox.setSelected(mySettings.SHOW_SYNTHETIC_FRAMES);
    myShowCurrentThreadChechBox.setSelected(mySettings.SHOW_CURRENT_THREAD);
  }

  public String getHelpTopic() {
    return null;
  }

  public void disposeUIResources() {
  }
}