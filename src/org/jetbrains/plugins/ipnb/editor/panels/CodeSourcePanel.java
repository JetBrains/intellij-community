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
package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author traff
 */
public class CodeSourcePanel extends JPanel implements EditorPanel {
  private final Editor myEditor;

  public CodeSourcePanel(Project project, Disposable parent, String source) {
    super(new BorderLayout());
    myEditor = IpnbEditorUtil.createPythonCodeEditor(project, source);
    add(myEditor.getComponent(), BorderLayout.CENTER);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        EditorFactory.getInstance().releaseEditor(myEditor);
      }
    });
  }

  @Override
  public Editor getEditor() {
    return myEditor;
  }
}
