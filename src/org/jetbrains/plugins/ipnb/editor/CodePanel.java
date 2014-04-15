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
package org.jetbrains.plugins.ipnb.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.CodeCell;
import org.jetbrains.plugins.ipnb.format.cells.output.CellOutput;

import javax.swing.*;
import java.awt.*;

/**
 * @author traff
 */
public class CodePanel extends JPanel implements EditorPanel {
  private final Editor myEditor;

  public CodePanel(Project project, CodeCell cell) {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    myEditor = IpnbEditorUtil.createPythonCodeEditor(project, StringUtil.join(cell.getInput(), "\n"));

    add(createContainer(inputPrompt(cell), myEditor.getComponent()));

    for (CellOutput output: cell.getCellOutputs()) {
      add(createContainer(outputPrompt(cell), new JTextArea(StringUtil.join(output.getText(), "\n"))));
    }


  }

  private JPanel createContainer(@NotNull String promptText, JComponent component) {
    JPanel container = new JPanel(new BorderLayout());
    JPanel p = new JPanel(new BorderLayout());
    p.add(new JLabel(promptText), BorderLayout.WEST);
    add(p, BorderLayout.NORTH);

    container.add(component, BorderLayout.CENTER);

    container.setMinimumSize(myEditor.getComponent().getPreferredSize());
    return container;
  }

  private String inputPrompt(@NotNull CodeCell cell) {
    return String.format("In[%d]:", cell.getPromptNumber());
  }

  private String outputPrompt(@NotNull CodeCell cell) {
    return String.format("Out[%d]:", cell.getPromptNumber());
  }

  @Override
  public Editor getEditor() {
    return myEditor;
  }
}
