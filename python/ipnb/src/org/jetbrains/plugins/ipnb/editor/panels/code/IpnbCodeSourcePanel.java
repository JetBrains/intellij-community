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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.reference.SoftReference;
import com.intellij.ui.Gray;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.actions.*;
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
  @NotNull private final SoftReference<IpnbCodePanel> myParent;

  public IpnbCodeSourcePanel(@NotNull final Project project, @NotNull final IpnbCodePanel parent, @NotNull final IpnbCodeCell cell) {
    super(cell, new HorizontalLayout(5));
    myProject = project;
    myParent = new SoftReference<>(parent);
    final JComponent panel = createViewPanel();
    add(panel);
    addRightClickMenu();
  }

  public void addMouseListener(@NotNull final EditorMouseListener listener) {
    myEditor.addEditorMouseListener(listener);
  }

  @NotNull
  public IpnbCodePanel getIpnbCodePanel() {
    return SoftReference.dereference(myParent);
  }

  @Override
  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  public void dispose() {
    removeAll();
    EditorFactory.getInstance().releaseEditor(myEditor);
    myEditor = null;
  }

  @Override
  protected JComponent createViewPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(UIUtil.isUnderDarcula() ? IpnbEditorUtil.getBackground() : Gray._247);

    myEditor = IpnbEditorUtil.createPythonCodeEditor(myProject, this);
    final IpnbCodePanel codePanel = getIpnbCodePanel();
    final JComponent component = myEditor.getComponent();
    final JComponent contentComponent = myEditor.getContentComponent();

    new IpnbRunCellAction(codePanel.getFileEditor()).registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("shift ENTER")), contentComponent);
    new IpnbRunCellInplaceAction(codePanel.getFileEditor()).registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("ctrl ENTER")), contentComponent);

    contentComponent.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        final int keyCode = e.getKeyCode();
        final Container parent = codePanel.getParent();
        if (keyCode == KeyEvent.VK_ESCAPE && parent instanceof IpnbFilePanel) {
          getIpnbCodePanel().setEditing(false);
          IdeFocusManager.getGlobalInstance().requestFocus(getIpnbCodePanel().getFileEditor().getIpnbFilePanel(), true);
        }
      }

      private void updateVisibleArea(boolean up) {
        final IpnbFileEditor fileEditor = codePanel.getFileEditor();
        final IpnbFilePanel ipnbPanel = fileEditor.getIpnbFilePanel();
        final Rectangle rect = ipnbPanel.getVisibleRect();

        final Rectangle cellBounds = IpnbCodeSourcePanel.this.getIpnbCodePanel().getBounds();
        final JScrollPane scrollPane = fileEditor.getScrollPane();

        final int y = cellBounds.y + myEditor.visualPositionToXY(myEditor.getCaretModel().getVisualPosition()).y;
        int delta = myEditor.getLineHeight() * 2;
        if (y <= rect.getY() && up) {
          scrollPane.getVerticalScrollBar().setValue(y);
        }
        if (y + delta > rect.getY() + rect.getHeight() && !up) {
          scrollPane.getVerticalScrollBar().setValue(y - rect.height + delta);
        }
      }

      @Override
      public void keyReleased(KeyEvent e) {
        final int keyCode = e.getKeyCode();
        final Container parent = codePanel.getParent();

        final int height = myEditor.getLineHeight() * Math.max(myEditor.getDocument().getLineCount(), 1) + 10;
        contentComponent.setPreferredSize(new Dimension(parent.getWidth() - 300, height));
        panel.setPreferredSize(new Dimension(parent.getWidth() - 300, height));

        if (parent instanceof IpnbFilePanel) {
          IpnbFilePanel ipnbFilePanel = (IpnbFilePanel)parent;
          if (keyCode == KeyEvent.VK_ENTER && InputEvent.CTRL_MASK == e.getModifiers()) {
            IpnbRunCellBaseAction.runCell(ipnbFilePanel, false);
          }
          else if (keyCode == KeyEvent.VK_ENTER && InputEvent.SHIFT_DOWN_MASK == e.getModifiersEx()) {
            IpnbRunCellBaseAction.runCell(ipnbFilePanel, true);
          }
          else if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_PAGE_DOWN ||
                   keyCode == KeyEvent.VK_PAGE_UP) {
            updateVisibleArea(keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_PAGE_UP);
          }
        }
      }
    });

    contentComponent.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (InputEvent.CTRL_DOWN_MASK == e.getModifiersEx()) return;
        final Container ipnbFilePanel = codePanel.getParent();
        if (ipnbFilePanel instanceof IpnbFilePanel) {
          ((IpnbFilePanel)ipnbFilePanel).setSelectedCell(codePanel, true);
          codePanel.switchToEditing();
        }
        UIUtil.requestFocus(contentComponent);
      }
    });

    panel.add(component);
    contentComponent.addHierarchyListener(new HierarchyListener() {
      @Override
      public void hierarchyChanged(HierarchyEvent e) {
        final Container parent = codePanel.getParent();
        if (parent != null && myEditor != null) {
          final int height = myEditor.getLineHeight() * Math.max(myEditor.getDocument().getLineCount(), 1) + 10;
          contentComponent.setPreferredSize(new Dimension(parent.getWidth() - 300, height));
          panel.setPreferredSize(new Dimension(parent.getWidth() - 300, height));
        }
      }
    });
    contentComponent.addHierarchyBoundsListener(new HierarchyBoundsAdapter() {
      @Override
      public void ancestorResized(HierarchyEvent e) {
        final Container parent = codePanel.getParent();
        final Component component = e.getChanged();
        if (parent != null && component instanceof IpnbFilePanel && myEditor != null) {
          final int height = myEditor.getLineHeight() * Math.max(myEditor.getDocument().getLineCount(), 1) + 10;
          contentComponent.setPreferredSize(new Dimension(parent.getWidth() - 300, height));
          panel.setPreferredSize(new Dimension(parent.getWidth() - 300, height));
          panel.revalidate();
          panel.repaint();
        }
      }
    });
    return panel;
  }

  @Override
  protected void addRightClickMenu() {
    addMouseListener(new EditorMouseAdapter() {
      @Override
      public void mousePressed(EditorMouseEvent e) {
        final MouseEvent mouseEvent = e.getMouseEvent();
        if (SwingUtilities.isRightMouseButton(mouseEvent) && mouseEvent.getClickCount() == 1) {
          final ListPopup menu = createPopupMenu(new DefaultActionGroup(new IpnbMergeCellAboveAction(), new IpnbMergeCellBelowAction(),
                                                                        new IpnbSplitCellAction(),
                                                                        new IpnbToggleLineNumbersAction(myParent.get())));
          menu.show(RelativePoint.fromScreen(e.getMouseEvent().getLocationOnScreen()));
        }
      }
    });
  }

  public ListPopup createPopupMenu(@NotNull DefaultActionGroup group) {
    final DataContext context = DataManager.getInstance().getDataContext(this);
    return JBPopupFactory.getInstance().createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.MNEMONICS,
                                                               false);
  }
}
