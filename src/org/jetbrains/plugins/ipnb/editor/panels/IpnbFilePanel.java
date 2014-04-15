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
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.IpnbFile;
import org.jetbrains.plugins.ipnb.format.cells.CodeCell;
import org.jetbrains.plugins.ipnb.format.cells.HeadingCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;
import org.jetbrains.plugins.ipnb.format.cells.MarkdownCell;
import org.jetbrains.plugins.ipnb.format.cells.output.CellOutput;

import javax.swing.*;
import java.awt.*;

/**
 * @author traff
 */
public class IpnbFilePanel extends JPanel {

  public static final int INSET_Y = 10;
  public static final int INSET_X = 3;
  private Project myProject;
  @Nullable private Disposable myParent;

  public IpnbFilePanel(@NotNull Project project, @Nullable Disposable parent, @NotNull IpnbFile file) {
    super();
    myProject = project;
    myParent = parent;
    setLayout(new GridBagLayout());
    setBackground(IpnbEditorUtil.getBackground());

    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.PAGE_START;
    c.gridx = 1;
    c.gridy = 0;
    c.gridwidth = 1;

    c.insets = new Insets(INSET_Y, INSET_X, 0, 0);

    for (IpnbCell cell : file.getCells()) {
      c.gridy = addCellToPanel(cell, c);
    }

    c.weighty = 1;
    add(createEmptyPanel(), c);
  }

  protected static String prompt(int promptNumber, String type) {
    return String.format(type + "[%d]:", promptNumber);
  }

  public static void addPromptPanel(JComponent container,
                                    int promptNumber,
                                    String promptType,
                                    JComponent component,
                                    GridBagConstraints c) {
    c.gridx = 0;
    container.add(IpnbEditorUtil.createPromptPanel(prompt(promptNumber, promptType)), c);
    c.gridx = 1;
    c.ipady = 10;
    c.weightx = 1;
    container.add(component, c);
    c.weightx = 0;
    c.ipady = 0;
  }

  private int addCellToPanel(IpnbCell cell, GridBagConstraints c) {
    if (cell instanceof CodeCell) {
      c.gridwidth = 2;
      c.gridx = 0;

      CodeCell codeCell = (CodeCell)cell;

      addPromptPanel(this, codeCell.getPromptNumber(), "In", new CodeSourcePanel(myProject, myParent, codeCell.getSourceAsString()), c);

      c.gridx = 1;
      c.gridwidth = 1;

      for (CellOutput cellOutput : codeCell.getCellOutputs()) {
        c.gridy++;
        if (cellOutput.getSourceAsString() != null) {
          addPromptPanel(this, codeCell.getPromptNumber(), "Out",
                         new CodeOutputPanel(cellOutput.getSourceAsString()), c);
        }
      }
    }
    else if (cell instanceof MarkdownCell) {
      add(new MarkdownPanel(myProject, (MarkdownCell)cell), c);
    }
    else if (cell instanceof HeadingCell) {
      add(new HeadingPanel(myProject, (HeadingCell)cell), c);
    }
    else {
      throw new UnsupportedOperationException(cell.getClass().toString());
    }
    return c.gridy + 1;
  }

  private JPanel createEmptyPanel() {
    JPanel panel = new JPanel();
    panel.setBackground(IpnbEditorUtil.getBackground());
    return panel;
  }
}
