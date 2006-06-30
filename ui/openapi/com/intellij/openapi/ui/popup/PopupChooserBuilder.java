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

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.TreeTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
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

  private boolean myRequestFocus = true;
  private boolean myForceResizable = false;
  private boolean myForceMovable = false;
  private String myDimensionServiceKey = null;

  public PopupChooserBuilder(@NotNull JList list) {
    myChooserComponent = new MyListWrapper(list);
  }

  public PopupChooserBuilder(@NotNull JTable table) {
    myChooserComponent = table;
  }

  public PopupChooserBuilder(@NotNull JTree tree) {
    myChooserComponent = tree;
  }

  @NotNull
  public PopupChooserBuilder setTitle(@NotNull String title) {
    myTitle = title;
    return this;
  }

  @NotNull
  public PopupChooserBuilder addAdditionalChooseKeystroke(@Nullable KeyStroke keyStroke) {
    if (keyStroke != null) {
      myAdditionalKeystrokes.add(keyStroke);
    }
    return this;
  }

  @NotNull
  public PopupChooserBuilder setItemChoosenCallback(@NotNull Runnable runnable) {
    myItemChoosenRunnable = runnable;
    return this;
  }

  @NotNull
  public PopupChooserBuilder setSouthComponent(@NotNull JComponent cmp) {
    mySouthComponent = cmp;
    return this;
  }


  public PopupChooserBuilder setRequestFocus(final boolean requestFocus) {
    myRequestFocus = requestFocus;
    return this;
  }

  public PopupChooserBuilder setResizable(final boolean forceResizable) {
    myForceResizable = forceResizable;
    return this;
  }


  public PopupChooserBuilder setMovable(final boolean forceMovable) {
    myForceMovable = forceMovable;
    return this;
  }

  public PopupChooserBuilder setDimensionServiceKey(String key){
    myDimensionServiceKey = key;
    return this;
  }

  @NotNull
  public JBPopup createPopup() {
    JPanel contentPane = new JPanel(new BorderLayout());
    if (!myForceMovable && myTitle != null) {
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

    regsiterClosePopupKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), false);
    regsiterClosePopupKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), true);
    for (KeyStroke keystroke : myAdditionalKeystrokes) {
      regsiterClosePopupKeyboardAction(keystroke, true);
    }

    myChooserComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    final JScrollPane scrollPane;
    if (myChooserComponent instanceof MyListWrapper) {
      scrollPane = (MyListWrapper)myChooserComponent;
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

    myPopup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(contentPane, myChooserComponent)
      .setDimensionServiceKey(myDimensionServiceKey)
      .setRequestFocus(myRequestFocus)
      .setResizable(myForceResizable)
      .setMovable(myForceMovable)
      .setTitle(myForceMovable ? myTitle : null)
      .createPopup();
    return myPopup;
  }

  private void regsiterClosePopupKeyboardAction(final KeyStroke keyStroke, final boolean shouldPerformAction) {
    myChooserComponent.registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        closePopup(shouldPerformAction);
      }
    }, keyStroke, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void closePopup(boolean shouldPerformAction) {
    myPopup.cancel();

    if (shouldPerformAction) {
      if (myItemChoosenRunnable != null) {
        myItemChoosenRunnable.run();
      }
    }
  }

  @NotNull
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

  @NotNull
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

  private static class MyListWrapper extends JScrollPane implements DataProvider {
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private JList myList;

    public MyListWrapper(final JList list) {
      super(list);
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
      setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
      if (modelSize > 0 && modelSize <= 20) {
        list.setVisibleRowCount(0);
        getViewport().setPreferredSize(list.getPreferredSize());
      }
      else {
        list.setVisibleRowCount(20);
      }
      myList = list;
    }


    @Nullable
    public Object getData(@NonNls String dataId) {
      if (dataId.equals(DataConstants.PSI_ELEMENT)){
        final Object selectedValue = myList.getSelectedValue();
        if (selectedValue instanceof PsiElement){
          return selectedValue;
        }
      }
      return null;
    }

    public void setBorder(Border border) {
      if (myList != null){
        myList.setBorder(border);
      }
    }

    public void requestFocus() {
      myList.requestFocus();
    }

    public synchronized void addMouseListener(MouseListener l) {
      myList.addMouseListener(l);
    }
  }
}
