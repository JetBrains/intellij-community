/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.openapi.ui.popup;

import com.intellij.ui.ListScrollingUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.TreeTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

/**
 * @author max
 */
public class PopupChooserBuilder {
  private JComponent myChooserComponent;
  private String myTitle;
  private ArrayList<KeyStroke> myAdditionalKeystrokes = new ArrayList<KeyStroke>();
  private Runnable myItemChoosenRunnable;
  private JComponent mySouthComponent;

  private JBPopup myPopup;

  public PopupChooserBuilder(@NotNull JList list) {
    myChooserComponent = list;
  }

  public PopupChooserBuilder(@NotNull JTable table) {
    myChooserComponent = table;
  }

  public PopupChooserBuilder(@NotNull JTree tree) {
    myChooserComponent = tree;
  }

  public PopupChooserBuilder setTitle(@NotNull String title) {
    myTitle = title;
    return this;
  }

  public PopupChooserBuilder addAdditionalChooseKeystroke(@Nullable KeyStroke keyStroke) {
    if (keyStroke != null) {
      myAdditionalKeystrokes.add(keyStroke);
    }
    return this;
  }

  public PopupChooserBuilder setItemChoosenCallback(@NotNull Runnable runnable) {
    myItemChoosenRunnable = runnable;
    return this;
  }

  public PopupChooserBuilder setSouthComponent(@NotNull JComponent cmp) {
    mySouthComponent = cmp;
    return this;
  }

  public JBPopup createPopup() {
    JPanel contentPane = new JPanel(new BorderLayout());
    if (myTitle != null) {
      JLabel label = new JLabel(myTitle);
      label.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
      label.setHorizontalAlignment(JLabel.CENTER);
      contentPane.add(label, BorderLayout.NORTH);
    }

    myChooserComponent.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    myChooserComponent.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (!e.isConsumed()) {
          closePopup(true);
        }
      }
    });

    myChooserComponent.addKeyListener(new KeyAdapter() {
      public void keyTyped(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyChar()) {
          closePopup(true);
          e.consume();
        }
      }

      public void keyPressed(KeyEvent e) {
        KeyStroke keyStroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
        for (KeyStroke keyStroke2 : myAdditionalKeystrokes) {
          if (keyStroke2.equals(keyStroke)) {
            e.consume();
            closePopup(true);
            return;
          }
        }
      }
    });

    myChooserComponent.registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        closePopup(false);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myChooserComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    final JScrollPane scrollPane;
    if (myChooserComponent instanceof JList) {
      scrollPane = createScrollPane((JList)myChooserComponent);
    }
    else if (myChooserComponent instanceof JTable) {
      scrollPane = createScrollPane((JTable)myChooserComponent);
    }
    else if (myChooserComponent instanceof JTree) {
      scrollPane = createScrollPane((JTree)myChooserComponent);
    }
    else {
      throw new IllegalStateException("PopupChooserBuilder is intended to be constructed with one of JTable, JTree, JList components");
    }

    contentPane.add(scrollPane, BorderLayout.CENTER);

    if (mySouthComponent != null) {
      contentPane.add(mySouthComponent, BorderLayout.SOUTH);
    }

    myPopup = JBPopupFactory.getInstance().createComponentPopup(contentPane, myChooserComponent, true);
    return myPopup;
  }

  private void closePopup(boolean shouldPerformAction) {
    myPopup.cancel();

    if (shouldPerformAction) {
      if (myItemChoosenRunnable != null) {
        myItemChoosenRunnable.run();
      }
    }
  }

  public static JScrollPane createScrollPane(final JTable table) {
    if (table instanceof TreeTable) {
      TreeUtil.expandAll(((TreeTable)table).getTree());
    }

    JScrollPane scrollPane = new JScrollPane(table);

    scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

    if (table.getSelectedRow() == -1) {
      table.getSelectionModel().setSelectionInterval(0, 0);
    }

    if (table.getRowCount() >= 20) {
      scrollPane.getViewport().setPreferredSize(new Dimension(table.getPreferredScrollableViewportSize().width, 300));
    }
    else {
      scrollPane.getViewport().setPreferredSize(table.getPreferredSize());
    }

    table.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e) {
        int index = table.rowAtPoint(e.getPoint());
        table.getSelectionModel().setSelectionInterval(index, index);
      }
    });

    return scrollPane;
  }

  public static JScrollPane createScrollPane(final JTree tree) {
    TreeUtil.expandAll(tree);

    JScrollPane scrollPane = new JScrollPane(tree);

    scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

    if (tree.getSelectionCount() == 0) {
      tree.setSelectionRow(0);
    }

    if (tree.getRowCount() >= 20) {
      scrollPane.getViewport().setPreferredSize(new Dimension(tree.getPreferredScrollableViewportSize().width, 300));
    }
    else {
      scrollPane.getViewport().setPreferredSize(tree.getPreferredSize());
    }

    tree.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e) {
        final Point p = e.getPoint();
        int index = tree.getRowForLocation(p.x, p.y);
        tree.setSelectionRow(index);
      }
    });

    return scrollPane;
  }

  public static JScrollPane createScrollPane(final JList list) {
    list.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e) {
        Point point = e.getPoint();
        int index = list.locationToIndex(point);
        list.setSelectedIndex(index);
      }
    });

    ListScrollingUtil.installActions(list);

    if (list.getSelectedIndex() == -1) {
      list.setSelectedIndex(0);
    }

    int modelSize = list.getModel().getSize();
    JScrollPane scrollPane = new JScrollPane(list);
    scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    if (modelSize > 0 && modelSize <= 20) {
      list.setVisibleRowCount(0);
      scrollPane.getViewport().setPreferredSize(list.getPreferredSize());
    }
    else {
      list.setVisibleRowCount(20);
    }

    return scrollPane;
  }
}
