/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author traff
 */
public class PyDebuggerConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final PyDebuggerOptionsProvider mySettings;
  private JPanel myMainPanel;
  private JCheckBox myAttachToSubprocess;
  private JCheckBox mySaveSignatures;
  private JButton myClearCacheButton;
  private JCheckBox mySupportGevent;
  private JBCheckBox mySupportQt;

  private final Project myProject;

  public PyDebuggerConfigurable(Project project, final PyDebuggerOptionsProvider settings) {
    myProject = project;
    mySettings = settings;
  }

  public String getDisplayName() {
    return "Python Debugger";
  }

  public String getHelpTopic() {
    return "reference.idesettings.debugger.python";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public JComponent createComponent() {
    myClearCacheButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        PySignatureCacheManager.getInstance(myProject).clearCache();
      }
    });
    return myMainPanel;
  }

  public boolean isModified() {
    return myAttachToSubprocess.isSelected() != mySettings.isAttachToSubprocess() ||
           mySaveSignatures.isSelected() != mySettings.isSaveCallSignatures() ||
           mySupportGevent.isSelected() != mySettings.isSupportGeventDebugging() ||
           mySupportQt.isSelected() != mySettings.isSupportQtDebugging();
  }

  public void apply() throws ConfigurationException {
    mySettings.setAttachToSubprocess(myAttachToSubprocess.isSelected());
    mySettings.setSaveCallSignatures(mySaveSignatures.isSelected());
    mySettings.setSupportGeventDebugging(mySupportGevent.isSelected());
    mySettings.setSupportQtDebugging(mySupportQt.isSelected());
  }

  public void reset() {
    myAttachToSubprocess.setSelected(mySettings.isAttachToSubprocess());
    mySaveSignatures.setSelected(mySettings.isSaveCallSignatures());
    mySupportGevent.setSelected(mySettings.isSupportGeventDebugging());
    mySupportQt.setSelected(mySettings.isSupportQtDebugging());
  }

  public void disposeUIResources() {
  }
}
