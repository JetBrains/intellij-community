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
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBUI;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PyDebugValue;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PyDataViewDialog extends DialogWrapper {
  private final JSplitPane myMainPanel;
  private static final int TABLE_DEFAULT_WIDTH = 700;
  private static final int TABLE_DEFAULT_HEIGHT = 500;

  PyDataViewDialog(@NotNull Project project, @NotNull final PyDebugValue value) {
    super(project, false);
    setModal(false);
    setCancelButtonText(PyBundle.message("debugger.data.view.close"));
    setCrossClosesWindow(true);
    myMainPanel = new JSplitPane();
    myMainPanel.setOrientation(JSplitPane.VERTICAL_SPLIT);
    final PyDataViewerPanel panel = new PyDataViewerPanel(project, value.getFrameAccessor());
    panel.apply(value);
    panel.setPreferredSize(JBUI.size(TABLE_DEFAULT_WIDTH, TABLE_DEFAULT_HEIGHT));
    myMainPanel.add(panel, JSplitPane.TOP);
    final JBCheckBox colored = new JBCheckBox(PyBundle.message("debugger.data.view.colored.cells"));
    final JBCheckBox resize = new JBCheckBox(PyBundle.message("debugger.data.view.resize.automatically"));
    resize.setSelected(PropertiesComponent.getInstance(project).getBoolean(PyDataView.AUTO_RESIZE, true));
    colored.setSelected(PropertiesComponent.getInstance(project).getBoolean(PyDataView.COLORED_BY_DEFAULT, true));
    colored.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        panel.setColored(colored.isSelected());
        panel.updateUI();
      }
    });
    resize.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        panel.resize(resize.isSelected());
        panel.updateUI();
      }
    });
    JPanel buttonsPanel = new JPanel(new VerticalFlowLayout());
    buttonsPanel.add(colored);
    buttonsPanel.add(resize);
    myMainPanel.add(buttonsPanel, JSplitPane.BOTTOM);
    setTitle(value.getFullName());
    init();
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getCancelAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
