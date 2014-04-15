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
import org.apache.commons.lang.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.format.IpnbFile;
import org.jetbrains.plugins.ipnb.format.cells.CodeCell;
import org.jetbrains.plugins.ipnb.format.cells.HeadingCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;
import org.jetbrains.plugins.ipnb.format.cells.MarkdownCell;

import javax.swing.*;
import java.awt.*;

/**
 * @author traff
 */
public class IpnbFilePanel extends JPanel {
  public IpnbFilePanel(@NotNull Project project, @Nullable Disposable parent, @NotNull IpnbFile file) {
    super();
    setLayout(new GridBagLayout());

    int row = 0;
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.PAGE_START;
    c.gridx = 0;

    for (IpnbCell cell : file.getCells()) {
      JPanel panel = createPanelForCell(project, cell);


      c.gridy = row;
      row++;
      add(panel, c);
    }

    c.weighty = 1;
    add(new JPanel(), c);
  }

  private JPanel createPanelForCell(@NotNull Project project, IpnbCell cell) {
    if (cell instanceof CodeCell) {
      return new CodePanel(project, (CodeCell)cell);
    }
    else if (cell instanceof MarkdownCell) {
      return new MarkdownPanel(project, (MarkdownCell)cell);
    }
    else if (cell instanceof HeadingCell) {
      return new HeadingPanel(project, (HeadingCell)cell);
    }
    else {
      throw new NotImplementedException(cell.getClass().toString());
    }
  }
}
