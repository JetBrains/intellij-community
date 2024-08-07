// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.palette.impl;

import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItem;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.PopupHandler;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.*;


public class PaletteGroupHeader extends JCheckBox implements UiDataProvider {
  private final PaletteWindow myPaletteWindow;
  private PaletteComponentList myComponentList;
  private final PaletteGroup myGroup;

  public PaletteGroupHeader(PaletteWindow paletteWindow, PaletteGroup group) {
    myPaletteWindow = paletteWindow;
    myGroup = group;
    if (group.getName() == null) {
       setVisible(false);
    }
    else {
      setText(group.getName());
    }
    setSelected(true);
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myComponentList != null) {
          myComponentList.setVisible(isSelected());
        }
      }
    });

    addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        myPaletteWindow.setLastFocusedGroup(PaletteGroupHeader.this);
        showGroupPopupMenu(comp, x, y);
      }
    });

    setIcon(UIUtil.getTreeCollapsedIcon());
    setSelectedIcon(UIUtil.getTreeExpandedIcon());
    setFont(getFont().deriveFont(Font.BOLD));
    setFocusPainted(false);
    setMargin(new Insets(0, 3, 0, 3));
    setOpaque(true);
    if (getBorder() instanceof CompoundBorder) { // from BasicLookAndFeel
      Dimension pref = getPreferredSize();
      pref.height -= 3;
      setPreferredSize(pref);
    }

    DnDManager.getInstance().registerTarget(new DnDTarget() {
      @Override
      public boolean update(DnDEvent aEvent) {
        setBorderPainted(true);
        aEvent.setDropPossible(aEvent.getAttachedObject() instanceof PaletteItem);
        return true;
      }

      @Override
      public void drop(DnDEvent aEvent) {
        setBorderPainted(false);
        if (aEvent.getAttachedObject() instanceof PaletteItem) {
          myGroup.handleDrop(myPaletteWindow.getProject(), (PaletteItem) aEvent.getAttachedObject(), -1);
        }
      }

      @Override
      public void cleanUpOnLeave() {
        setBorderPainted(false);
      }
    }, this);

    addFocusListener(new FocusAdapter() {
      @Override public void focusGained(FocusEvent e) {
        myPaletteWindow.setLastFocusedGroup(PaletteGroupHeader.this);
      }
    });

    initActions();
  }

  public void showGroupPopupMenu(final Component comp, final int x, final int y) {
    ActionGroup group = myGroup.getPopupActionGroup();
    if (group != null) {
      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("PaletteGroupHeader", group);
      popupMenu.getComponent().show(comp, x, y);
    }
  }

  private void initActions() {
    @NonNls InputMap inputMap = getInputMap(WHEN_FOCUSED);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, false), "moveFocusDown");
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false), "moveFocusUp");
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "collapse");
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "expand");

    @NonNls ActionMap actionMap = getActionMap();
    actionMap.put("moveFocusDown", new MoveFocusAction(true));
    actionMap.put("moveFocusUp", new MoveFocusAction(false));
    actionMap.put("collapse", new ExpandAction(false));
    actionMap.put("expand", new ExpandAction(true));
  }

  @Override public Color getBackground() {
    if (isFocusOwner()) {
      return UIUtil.getListSelectionBackground(true);
    }
    return super.getBackground();
  }

  @Override public Color getForeground() {
    if (isFocusOwner()) {
      return NamedColorUtil.getListSelectionForeground(true);
    }
    return super.getForeground();
  }

  public void setComponentList(final PaletteComponentList componentList) {
    myComponentList = componentList;
  }

  public PaletteComponentList getComponentList() {
    return myComponentList;
  }

  public PaletteGroup getGroup() {
    return myGroup;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    DataSink.uiDataSnapshot(sink, myPaletteWindow);
    myGroup.uiDataSnapshot(sink, myPaletteWindow.getProject());
  }

  private class MoveFocusAction extends AbstractAction {
    private final boolean moveDown;

    MoveFocusAction(boolean moveDown) {
      this.moveDown = moveDown;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      Container container = kfm.getCurrentFocusCycleRoot();
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
        if (null == policy) policy = kfm.getDefaultFocusTraversalPolicy();
        Component next =
          moveDown ? policy.getComponentAfter(container, PaletteGroupHeader.this) : policy.getComponentBefore(container, PaletteGroupHeader.this);
        if (next instanceof PaletteComponentList list) {
          if (list.getModel().getSize() != 0) {
            list.takeFocusFrom(PaletteGroupHeader.this, list == myComponentList ? 0 : -1);
            return;
          }
          else {
            next = moveDown ? policy.getComponentAfter(container, next) : policy.getComponentBefore(container, next);
          }
        }
        if (next instanceof PaletteGroupHeader) {

          IdeFocusManager.getGlobalInstance().requestFocus(next, true);
        }
      });
    }
  }

  private class ExpandAction extends AbstractAction {
    private final boolean expand;

    ExpandAction(boolean expand) {
      this.expand = expand;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (expand == isSelected()) return;
      setSelected(expand);
      if (myComponentList != null) {
        myComponentList.setVisible(isSelected());
      }
    }
  }
}
