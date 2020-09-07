/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.debugger.containerview;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBUI;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyFrameAccessor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PyDataViewDialog extends DialogWrapper {
  private final JPanel myMainPanel;
  private static final int TABLE_DEFAULT_WIDTH = 700;
  private static final int TABLE_DEFAULT_HEIGHT = 500;
  private final Project myProject;
  private final PyDataViewerPanel myDataViewerPanel;

  public PyDataViewDialog(@NotNull Project project, @NotNull final PyDebugValue value) {
    super(project, false);
    myProject = project;
    setModal(false);
    setCancelButtonText(PyBundle.message("debugger.data.view.close"));
    setCrossClosesWindow(true);

    myMainPanel = new JPanel(new GridBagLayout());
    myDataViewerPanel = createDataViewerPanel(project, value.getFrameAccessor());
    myDataViewerPanel.apply(value, false);
    myDataViewerPanel.setPreferredSize(JBUI.size(TABLE_DEFAULT_WIDTH, TABLE_DEFAULT_HEIGHT));

    myMainPanel.add(myDataViewerPanel, createDataViewPanelConstraints());

    myDataViewerPanel.addListener(new PyDataViewerPanel.Listener() {
      @Override
      public void onNameChanged(String name) {
        setTitle(name);
      }
    });

    addBottomElements();

    setTitle(value.getFullName());
    init();
  }

  protected void addBottomElements() {
    final JBCheckBox colored = new JBCheckBox(PyBundle.message("debugger.data.view.colored.cells"));
    final JBCheckBox resize = new JBCheckBox(PyBundle.message("debugger.data.view.resize.automatically"));

    resize.setSelected(PropertiesComponent.getInstance(myProject).getBoolean(PyDataView.AUTO_RESIZE, true));
    colored.setSelected(PropertiesComponent.getInstance(myProject).getBoolean(PyDataView.COLORED_BY_DEFAULT, true));

    colored.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDataViewerPanel.setColored(colored.isSelected());
        myDataViewerPanel.updateUI();
      }
    });
    resize.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDataViewerPanel.resize(resize.isSelected());
        myDataViewerPanel.updateUI();
      }
    });

    GridBagConstraints checkBoxConstraints = createCheckBoxConstraints();
    checkBoxConstraints.gridy = 1;
    myMainPanel.add(colored, checkBoxConstraints);
    checkBoxConstraints.gridy = 2;
    myMainPanel.add(resize, checkBoxConstraints);
  }

  protected PyDataViewerPanel createDataViewerPanel(@NotNull Project project, @NotNull PyFrameAccessor frameAccessor) {
    return new PyDataViewerPanel(project, frameAccessor);
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getCancelAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  protected GridBagConstraints createDataViewPanelConstraints() {
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 0.9;
    c.weighty = 0.9;
    return c;
  }

  protected GridBagConstraints createCheckBoxConstraints() {
    GridBagConstraints c = new GridBagConstraints();
    c.weighty = 0.05;
    c.anchor = GridBagConstraints.LINE_START;
    return c;
  }
}
