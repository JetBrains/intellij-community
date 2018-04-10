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
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.jetbrains.python.debugger.PyDebugValue;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PyDataViewDialog extends DialogWrapper {
  private final JPanel myMainPanel;

  PyDataViewDialog(@NotNull Project project, @NotNull final PyDebugValue value) {
    super(project, false);
    setModal(false);
    setCancelButtonText("Close");
    setCrossClosesWindow(true);
    myMainPanel = new JPanel(new VerticalFlowLayout());
    final PyDataViewerPanel panel = new PyDataViewerPanel(project, value.getFrameAccessor());
    panel.apply(value);
    myMainPanel.add(panel);
    panel.setBorder(BorderFactory.createLineBorder(JBColor.GRAY));
    final JBCheckBox colored = new JBCheckBox("Colored cells");
    final JBCheckBox resize = new JBCheckBox("Resize Automatically");
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
    myMainPanel.add(colored);
    myMainPanel.add(resize);
    setTitle(value.getFullName());
    init();
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getCancelAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
