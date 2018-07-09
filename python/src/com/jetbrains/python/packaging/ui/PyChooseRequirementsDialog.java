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
package com.jetbrains.python.packaging.ui;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.python.packaging.PyRequirement;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author vlan
 */
public class PyChooseRequirementsDialog extends DialogWrapper {
  private final ElementsChooser<PyRequirement> myRequirementsChooser;

  public PyChooseRequirementsDialog(@NotNull Project project, @NotNull List<PyRequirement> requirements) {
    super(project, false);
    setTitle("Choose Packages to Install");
    setOKButtonText("Install");
    myRequirementsChooser = new ElementsChooser<PyRequirement>(true) {
      @Override
      public String getItemText(@NotNull PyRequirement requirement) {
        return requirement.getPresentableText();
      }
    };
    myRequirementsChooser.setElements(requirements, true);
    myRequirementsChooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<PyRequirement>() {
      @Override
      public void elementMarkChanged(PyRequirement element, boolean isMarked) {
        setOKActionEnabled(!myRequirementsChooser.getMarkedElements().isEmpty());
      }
    });
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setPreferredSize(new Dimension(400, 300));
    final JBLabel label = new JBLabel("Choose packages to install:");
    label.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
    panel.add(label, BorderLayout.NORTH);
    panel.add(myRequirementsChooser, BorderLayout.CENTER);
    return panel;
  }

  public List<PyRequirement> getMarkedElements() {
    return myRequirementsChooser.getMarkedElements();
  }
}
