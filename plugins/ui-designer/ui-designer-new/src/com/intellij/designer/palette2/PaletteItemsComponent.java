/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.palette2;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * @author Alexander Lobas
 */
public class PaletteItemsComponent extends JBList {
  private final PaletteGroup myGroup;

  public PaletteItemsComponent(PaletteGroup group) {
    myGroup = group;

    setModel(new AbstractListModel() {
      @Override
      public int getSize() {
        return myGroup.getItems().size();
      }

      @Override
      public Object getElementAt(int index) {
        return myGroup.getItems().get(index);
      }
    });
    setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        clear();
        PaletteItem item = (PaletteItem)value;
        setIcon(item.getIcon());
        append(item.getTitle(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setToolTipText(item.getTooltip());
      }
    });

    setVisibleRowCount(0);
    setLayoutOrientation(HORIZONTAL_WRAP);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    initActions();
  }

  Integer myTempWidth;

  public int getWidth() {
    return (myTempWidth == null) ? super.getWidth() : myTempWidth.intValue();
  }

  public int getPreferredHeight(int width) {
    myTempWidth = width;
    try {
      return getUI().getPreferredSize(this).height;
    }
    finally {
      myTempWidth = null;
    }
  }

  public void takeFocusFrom(int indexToSelect) {
    if (indexToSelect == -1) {
      indexToSelect = getModel().getSize() - 1;
    }
    else if (getModel().getSize() == 0) {
      indexToSelect = -1;
    }
    requestFocus();
    setSelectedIndex(indexToSelect);
    if (indexToSelect >= 0) {
      ensureIndexIsVisible(indexToSelect);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private void initActions() {
    ActionMap map = getActionMap();
    map.put("selectPreviousRow", new MoveFocusAction(map.get("selectPreviousRow"), false));
    map.put("selectNextRow", new MoveFocusAction(map.get("selectNextRow"), true));
    map.put("selectPreviousColumn", new MoveFocusAction(new ChangeColumnAction(map.get("selectPreviousColumn"), false), false));
    map.put("selectNextColumn", new MoveFocusAction(new ChangeColumnAction(map.get("selectNextColumn"), true), true));
  }

  private class MoveFocusAction extends AbstractAction {
    private final Action myDefaultAction;
    private final boolean myFocusNext;

    public MoveFocusAction(Action defaultAction, boolean focusNext) {
      myDefaultAction = defaultAction;
      myFocusNext = focusNext;
    }

    public void actionPerformed(ActionEvent e) {
      int selIndexBefore = getSelectedIndex();
      myDefaultAction.actionPerformed(e);
      int selIndexCurrent = getSelectedIndex();
      if (selIndexBefore != selIndexCurrent) {
        return;
      }
      if (myFocusNext && selIndexCurrent == 0) {
        return;
      }

      KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      Container container = kfm.getCurrentFocusCycleRoot();
      FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
      if (policy == null) {
        policy = kfm.getDefaultFocusTraversalPolicy();
      }
      Component next = myFocusNext
                       ? policy.getComponentAfter(container, PaletteItemsComponent.this)
                       : policy.getComponentBefore(container, PaletteItemsComponent.this);
      if (next instanceof PaletteGroupComponent) {
        clearSelection();
        next.requestFocus();
        ((PaletteGroupComponent)next).scrollRectToVisible(next.getBounds());
      }
    }
  }

  private class ChangeColumnAction extends AbstractAction {
    private final Action myDefaultAction;
    private final boolean mySelectNext;

    public ChangeColumnAction(Action defaultAction, boolean selectNext) {
      myDefaultAction = defaultAction;
      mySelectNext = selectNext;
    }

    public void actionPerformed(ActionEvent e) {
      int selIndexBefore = getSelectedIndex();
      myDefaultAction.actionPerformed(e);
      int selIndexCurrent = getSelectedIndex();
      if (mySelectNext && selIndexBefore < selIndexCurrent || !mySelectNext && selIndexBefore > selIndexCurrent) {
        return;
      }

      if (mySelectNext) {
        if (selIndexCurrent == selIndexBefore + 1) {
          selIndexCurrent++;
        }
        if (selIndexCurrent < getModel().getSize() - 1) {
          setSelectedIndex(selIndexCurrent + 1);
          scrollRectToVisible(getCellBounds(selIndexCurrent + 1, selIndexCurrent + 1));
        }
      }
      else if (selIndexCurrent > 0) {
        setSelectedIndex(selIndexCurrent - 1);
        scrollRectToVisible(getCellBounds(selIndexCurrent - 1, selIndexCurrent - 1));
      }
    }
  }
}