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
package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbRunCellAction;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditorPanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbPanel;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCodeCell;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * @author traff
 */
public class IpnbCodeSourcePanel extends IpnbPanel<JComponent, IpnbCodeCell> implements IpnbEditorPanel {
  private Editor myEditor;
  @NotNull private final Project myProject;
  @NotNull private final IpnbCodePanel myParent;
  @NotNull private final String mySource;

  public IpnbCodeSourcePanel(@NotNull final Project project, @NotNull final IpnbCodePanel parent, @NotNull final IpnbCodeCell cell) {
    super(cell, new BorderLayout());
    myProject = project;
    myParent = parent;
    mySource = cell.getSourceAsString();
    final JComponent panel = createViewPanel();
    add(panel);
  }

  @NotNull
  public IpnbCodePanel getIpnbCodePanel() {
    return myParent;
  }

  @Override
  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  @Override
  protected JComponent createViewPanel() {
    final JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, true, true));
    panel.setBackground(UIUtil.isUnderDarcula() ? IpnbEditorUtil.getBackground() : Gray._247);

    if (mySource.startsWith("%")) {
      myEditor = IpnbEditorUtil.createPlainCodeEditor(myProject, mySource);
    }
    else {
      myEditor = IpnbEditorUtil.createPythonCodeEditor(myProject, this);
    }

    final JComponent component = myEditor.getComponent();
    final JComponent contentComponent = myEditor.getContentComponent();
    contentComponent.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        final int keyCode = e.getKeyCode();
        final int height = myEditor.getLineHeight() * Math.max(myEditor.getDocument().getLineCount(), 1);
        component.setPreferredSize(new Dimension(IpnbEditorUtil.PANEL_WIDTH, height));
        final Container parent = myParent.getParent();
        if (parent instanceof IpnbFilePanel) {
          IpnbFilePanel ipnbFilePanel = (IpnbFilePanel)parent;
          ipnbFilePanel.revalidate();
          ipnbFilePanel.repaint();
          if (keyCode == KeyEvent.VK_ESCAPE) {
            getIpnbCodePanel().setEditing(false);
            UIUtil.requestFocus(getIpnbCodePanel().getFileEditor().getIpnbFilePanel());
          }
          else if (keyCode == KeyEvent.VK_ENTER && InputEvent.CTRL_DOWN_MASK == e.getModifiersEx()) {
            final IpnbRunCellAction action = (IpnbRunCellAction)ActionManager.getInstance().getAction("IpnbRunCellAction");
            action.runCell(ipnbFilePanel, false);
          }
        }

      }
    });

    contentComponent.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (InputEvent.CTRL_DOWN_MASK == e.getModifiersEx()) return;
        final Container ipnbFilePanel = myParent.getParent();
        if (ipnbFilePanel instanceof IpnbFilePanel) {
          ((IpnbFilePanel)ipnbFilePanel).setSelectedCell(myParent);
          myParent.switchToEditing();
        }
        UIUtil.requestFocus(contentComponent);
      }
    });

    panel.add(component);

    component.setPreferredSize(new Dimension(IpnbEditorUtil.PANEL_WIDTH, component.getPreferredSize().height));
    setBorder(BorderFactory.createLineBorder(JBColor.lightGray, 1));
    return panel;
  }
}
