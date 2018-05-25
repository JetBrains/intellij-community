/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.TooltipWithClickableLinks;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.ActionLink;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

import static com.jetbrains.python.debugger.PyDebugSupportUtils.DEBUGGER_WARNING_MESSAGE;

/**
 * @author traff
 */
public class PyDebuggerConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final PyDebuggerOptionsProvider mySettings;
  private JPanel myMainPanel;
  private JCheckBox myAttachToSubprocess;
  private JCheckBox mySaveSignatures;
  private JCheckBox mySupportGevent;
  private JBCheckBox mySupportQt;
  private JBLabel warningIcon;
  private ComboBox<String> myPyQtBackend;
  private ActionLink myActionLink;
  private JBTextField myAttachProcessFilter;
  private JBLabel myAttachFilterLabel;
  private final List<String> myPyQtBackendsList = Lists.newArrayList("Auto", "PyQt4", "PyQt5", "PySide");

  private final Project myProject;

  public PyDebuggerConfigurable(Project project, final PyDebuggerOptionsProvider settings) {
    myProject = project;
    mySettings = settings;
    myPyQtBackendsList.forEach(e -> myPyQtBackend.addItem(e));

    mySupportQt.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myPyQtBackend.setEnabled(mySupportQt.isSelected());
      }
    });

    myAttachFilterLabel.setText("<html>For <b>Attach To Local Process</b> show processes with names containing:</html>");
  }

  public String getDisplayName() {
    return "Python Debugger";
  }

  @Override
  public String getHelpTopic() {
    return "reference.idesettings.debugger.python";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  public boolean isModified() {
    return myAttachToSubprocess.isSelected() != mySettings.isAttachToSubprocess() ||
           mySaveSignatures.isSelected() != mySettings.isSaveCallSignatures() ||
           mySupportGevent.isSelected() != mySettings.isSupportGeventDebugging() ||
           mySupportQt.isSelected() != mySettings.isSupportQtDebugging() ||
           (myPyQtBackend.getSelectedItem() != null && !myPyQtBackend.getSelectedItem().equals(mySettings.getPyQtBackend())) ||
           myAttachProcessFilter.getText() != mySettings.getAttachProcessFilter();
  }

  public void apply() throws ConfigurationException {
    mySettings.setAttachToSubprocess(myAttachToSubprocess.isSelected());
    mySettings.setSaveCallSignatures(mySaveSignatures.isSelected());
    mySettings.setSupportGeventDebugging(mySupportGevent.isSelected());
    mySettings.setSupportQtDebugging(mySupportQt.isSelected());
    mySettings.setPyQtBackend(myPyQtBackendsList.get(myPyQtBackend.getSelectedIndex()));
    mySettings.setAttachProcessFilter(myAttachProcessFilter.getText());
  }

  public void reset() {
    myAttachToSubprocess.setSelected(mySettings.isAttachToSubprocess());
    mySaveSignatures.setSelected(mySettings.isSaveCallSignatures());
    mySupportGevent.setSelected(mySettings.isSupportGeventDebugging());
    mySupportQt.setSelected(mySettings.isSupportQtDebugging());
    myPyQtBackend.setSelectedItem(mySettings.getPyQtBackend());
    myAttachProcessFilter.setText(mySettings.getAttachProcessFilter());
  }

  public void disposeUIResources() {
  }

  private void createUIComponents() {
    warningIcon = new JBLabel(AllIcons.General.BalloonWarning);
    IdeTooltipManager.getInstance().setCustomTooltip(
      warningIcon,
      new TooltipWithClickableLinks.ForBrowser(warningIcon,
                                               DEBUGGER_WARNING_MESSAGE));

    myActionLink = new ActionLink("Clear caches", new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        PySignatureCacheManager.getInstance(myProject).clearCache();
      }
    });
  }
}
