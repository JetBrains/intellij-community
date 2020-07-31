// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.TooltipWithClickableLinks;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.ActionLink;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

import static com.jetbrains.python.debugger.PyDebugSupportUtils.DEBUGGER_WARNING_MESSAGE;

public class PyDebuggerConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private JPanel myMainPanel;
  private JCheckBox myAttachToSubprocess;
  private JCheckBox mySaveSignatures;
  private JCheckBox mySupportGevent;
  private JBCheckBox mySupportQt;
  private JCheckBox myDropIntoDebuggerOnFailedTests;
  private JBLabel warningIcon;
  private ComboBox<String> myPyQtBackend;
  private ActionLink myActionLink;
  private JBTextField myAttachProcessFilter;
  private JBLabel myAttachFilterLabel;
  private final List<String> myPyQtBackendsList = Lists.newArrayList("Auto", "PyQt4", "PyQt5", "PySide", "PySide2");

  private final Project myProject;

  public PyDebuggerConfigurable(Project project) {
    myProject = project;
    myPyQtBackendsList.forEach(e -> myPyQtBackend.addItem(e));

    mySupportQt.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myPyQtBackend.setEnabled(mySupportQt.isSelected());
      }
    });

    myAttachFilterLabel.setText(PyBundle.message("debugger.attach.to.process.filter.names"));
  }

  @Override
  public String getDisplayName() {
    return PyBundle.message("configurable.PyDebuggerConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.idesettings.debugger.python";
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    PyDebuggerOptionsProvider settings = PyDebuggerOptionsProvider.getInstance(myProject);
    return myAttachToSubprocess.isSelected() != settings.isAttachToSubprocess() ||
           mySaveSignatures.isSelected() != settings.isSaveCallSignatures() ||
           mySupportGevent.isSelected() != settings.isSupportGeventDebugging() ||
           myDropIntoDebuggerOnFailedTests.isSelected() != settings.isDropIntoDebuggerOnFailedTest() ||
           mySupportQt.isSelected() != settings.isSupportQtDebugging() ||
           (myPyQtBackend.getSelectedItem() != null && !myPyQtBackend.getSelectedItem().equals(settings.getPyQtBackend())) ||
           !myAttachProcessFilter.getText().equals(settings.getAttachProcessFilter());
  }

  @Override
  public void apply() {
    PyDebuggerOptionsProvider settings = PyDebuggerOptionsProvider.getInstance(myProject);
    settings.setAttachToSubprocess(myAttachToSubprocess.isSelected());
    settings.setSaveCallSignatures(mySaveSignatures.isSelected());
    settings.setSupportGeventDebugging(mySupportGevent.isSelected());
    settings.setDropIntoDebuggerOnFailedTest(myDropIntoDebuggerOnFailedTests.isSelected());
    settings.setSupportQtDebugging(mySupportQt.isSelected());
    settings.setPyQtBackend(myPyQtBackendsList.get(myPyQtBackend.getSelectedIndex()));
    settings.setAttachProcessFilter(myAttachProcessFilter.getText());
  }

  @Override
  public void reset() {
    PyDebuggerOptionsProvider settings = PyDebuggerOptionsProvider.getInstance(myProject);
    myAttachToSubprocess.setSelected(settings.isAttachToSubprocess());
    mySaveSignatures.setSelected(settings.isSaveCallSignatures());
    mySupportGevent.setSelected(settings.isSupportGeventDebugging());
    myDropIntoDebuggerOnFailedTests.setSelected(settings.isDropIntoDebuggerOnFailedTest());
    mySupportQt.setSelected(settings.isSupportQtDebugging());
    myPyQtBackend.setSelectedItem(settings.getPyQtBackend());
    myAttachProcessFilter.setText(settings.getAttachProcessFilter());
  }

  @Override
  public void disposeUIResources() {
  }

  private void createUIComponents() {
    warningIcon = new JBLabel(AllIcons.General.BalloonWarning);
    IdeTooltipManager.getInstance().setCustomTooltip(
      warningIcon,
      new TooltipWithClickableLinks.ForBrowser(warningIcon,
                                               DEBUGGER_WARNING_MESSAGE));

    myActionLink = new ActionLink(PyBundle.message("form.debugger.clear.caches.action"), new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        PySignatureCacheManager.getInstance(myProject).clearCache();
      }
    });
  }
}
