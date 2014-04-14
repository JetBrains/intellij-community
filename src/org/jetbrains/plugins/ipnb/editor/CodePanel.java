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

import javax.swing.*;
import java.awt.*;

/**
 * @author traff
 */
public class CodePanel extends JPanel implements EditorPanel {
  private final Editor myEditor;

  public CodePanel(Project project, String prompt, String code) {
    setLayout(new BorderLayout());
    myEditor = IpnbEditorUtil.createPythonCodeEditor(project, code);

    JPanel p = new JPanel(new BorderLayout());
    p.add(new JLabel(prompt), BorderLayout.WEST);
    add(p, BorderLayout.NORTH);
    add(myEditor.getComponent(), BorderLayout.CENTER);

    setMinimumSize(myEditor.getComponent().getPreferredSize());
  }

  @Override
  public Editor getEditor() {
    return myEditor;
  }
}
