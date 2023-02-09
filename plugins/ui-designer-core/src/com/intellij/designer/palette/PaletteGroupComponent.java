// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.designer.palette;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.Gray;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * @author Alexander Lobas
 */
public class PaletteGroupComponent extends JCheckBox {
  private PaletteItemsComponent myItemsComponent;

  public PaletteGroupComponent(PaletteGroup group) {
    setText(group.getName());
    setSelected(true);
    setIcon(AllIcons.Nodes.Folder);
    setSelectedIcon(AllIcons.Nodes.Folder);
    setFont(getFont().deriveFont(Font.BOLD));
    setFocusPainted(false);
    setMargin(new Insets(0, 3, 0, 3));
    setOpaque(true);

    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myItemsComponent.setVisible(isSelected());
      }
    });

    initActions();
  }

  @Override
  public Color getBackground() {
    if (isFocusOwner()) {
      return UIUtil.getListSelectionBackground(true);
    }
    if (StartupUiUtil.isUnderDarcula()) {
      return Gray._100;
    }
    return super.getBackground();
  }

  @Override
  public Color getForeground() {
    if (isFocusOwner()) {
      return NamedColorUtil.getListSelectionForeground(true);
    }
    return super.getForeground();
  }

  public PaletteItemsComponent getItemsComponent() {
    return myItemsComponent;
  }

  public void setItemsComponent(PaletteItemsComponent itemsComponent) {
    myItemsComponent = itemsComponent;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private void initActions() {
    InputMap inputMap = getInputMap(WHEN_FOCUSED);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, false), "moveFocusDown");
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false), "moveFocusUp");
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "collapse");
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "expand");

    ActionMap actionMap = getActionMap();
    actionMap.put("moveFocusDown", new MoveFocusAction(true));
    actionMap.put("moveFocusUp", new MoveFocusAction(false));
    actionMap.put("collapse", new ExpandAction(false));
    actionMap.put("expand", new ExpandAction(true));
  }

  private class MoveFocusAction extends AbstractAction {
    private final boolean myMoveDown;

    MoveFocusAction(boolean moveDown) {
      myMoveDown = moveDown;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      Container container = kfm.getCurrentFocusCycleRoot();

      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {

        FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
        if (policy == null) {
          policy = kfm.getDefaultFocusTraversalPolicy();
        }

        Component next = myMoveDown
                         ? policy.getComponentAfter(container, PaletteGroupComponent.this)
                         : policy.getComponentBefore(container, PaletteGroupComponent.this);
        if (next instanceof PaletteItemsComponent list) {
          if (list.getModel().getSize() != 0) {
            list.takeFocusFrom(list == myItemsComponent ? 0 : -1);
            return;
          }
          else {
            next = myMoveDown ? policy.getComponentAfter(container, next) : policy.getComponentBefore(container, next);
          }
        }
        if (next instanceof PaletteGroupComponent) {
          IdeFocusManager.getGlobalInstance().requestFocus(next, true);
        }
      });
    }
  }

  private class ExpandAction extends AbstractAction {
    private final boolean myExpand;

    ExpandAction(boolean expand) {
      myExpand = expand;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (myExpand != isSelected()) {
        setSelected(myExpand);
        if (myItemsComponent != null) {
          myItemsComponent.setVisible(isSelected());
        }
      }
    }
  }
}