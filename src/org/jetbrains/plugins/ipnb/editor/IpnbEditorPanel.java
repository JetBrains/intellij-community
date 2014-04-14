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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author traff
 */
public class IpnbEditorPanel extends JPanel {
  public IpnbEditorPanel(@NotNull Project project, @Nullable Disposable parent, @NotNull List<String> cells) {
    super();
    setLayout(new GridBagLayout());

    int row = 0;
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.PAGE_START;
    c.gridx = 0;

//    c.weighty = 1;
    for (String cell : cells) {
      JPanel panel = cell.startsWith("#") ? new CodePanel(project, "In[1]:", cell)
              : new MarkdownPanel(project, cell);


      c.gridy = row;
      row++;
      add(panel, c);

//      Disposer.register(parent, new Disposable() {
//        @Override
//        public void dispose() {
//          final EditorFactory editorFactory = EditorFactory.getInstance();
//          editorFactory.releaseEditor(e);
//        }
//      });
    }

    c.weighty = 1;
    add(new JPanel(), c);
  }
}
