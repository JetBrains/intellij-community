/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.palette.impl;

import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItem;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.PopupHandler;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * @author yole
 */
public class PaletteGroupHeader extends JCheckBox implements DataProvider {
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
      public void actionPerformed(ActionEvent e) {
        if (myComponentList != null) {
          myComponentList.setVisible(isSelected());
        }
      }
    });

    addMouseListener(new PopupHandler() {
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
      public boolean update(DnDEvent aEvent) {
        setBorderPainted(true);
        aEvent.setDropPossible(aEvent.getAttachedObject() instanceof PaletteItem);
        return true;
      }

      public void drop(DnDEvent aEvent) {
        setBorderPainted(false);
        if (aEvent.getAttachedObject() instanceof PaletteItem) {
          myGroup.handleDrop(myPaletteWindow.getProject(), (PaletteItem) aEvent.getAttachedObject(), -1);
        }
      }

      public void cleanUpOnLeave() {
        setBorderPainted(false);
      }

      public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
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
      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
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
      return UIUtil.getListSelectionBackground();
    }
    return super.getBackground();
  }

  @Override public Color getForeground() {
    if (isFocusOwner()) {
      return UIUtil.getListSelectionForeground();
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

  @Nullable public Object getData(String dataId) {
    Object data = myPaletteWindow.getData(dataId);
    if (data != null) return data;
    Project project = CommonDataKeys.PROJECT.getData(myPaletteWindow);
    return myGroup.getData(project, dataId);
  }

  private class MoveFocusAction extends AbstractAction {
    private final boolean moveDown;

    public MoveFocusAction(boolean moveDown) {
      this.moveDown = moveDown;
    }

    public void actionPerformed(ActionEvent e) {
      KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      Container container = kfm.getCurrentFocusCycleRoot();
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
        if (null == policy) policy = kfm.getDefaultFocusTraversalPolicy();
        Component next =
          moveDown ? policy.getComponentAfter(container, PaletteGroupHeader.this) : policy.getComponentBefore(container, PaletteGroupHeader.this);
        if (next instanceof PaletteComponentList) {
          final PaletteComponentList list = (PaletteComponentList)next;
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

    public ExpandAction(boolean expand) {
      this.expand = expand;
    }

    public void actionPerformed(ActionEvent e) {
      if (expand == isSelected()) return;
      setSelected(expand);
      if (myComponentList != null) {
        myComponentList.setVisible(isSelected());
      }
    }
  }
}
