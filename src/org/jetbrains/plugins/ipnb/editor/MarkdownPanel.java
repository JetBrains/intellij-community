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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.plugins.ipnb.format.cells.MarkdownCell;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author traff
 */
public class MarkdownPanel extends JPanel {
  private boolean myEditing = false;
  private Project myProject;

  public MarkdownPanel(Project project, MarkdownCell cell) {
    super(new BorderLayout());
    myProject = project;
    add(createTextArea(StringUtil.join(cell.getSource(), "\n")), BorderLayout.CENTER);


    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
//          setEditing(true); TODO
          throw new IllegalStateException("not supported");
        }
      }
    });
  }

  private JTextArea createTextArea(String text) {
    JTextArea textArea = new JTextArea(text);
    textArea.setEditable(false);
    return textArea;
  }

  public boolean isEditing() {
    return myEditing;
  }

  public void setEditing(boolean isEditing) {
    myEditing = isEditing;
    updatePanel();
  }

  private void updatePanel() {
    removeAll();
    if (myEditing) {


    }
  }
}
