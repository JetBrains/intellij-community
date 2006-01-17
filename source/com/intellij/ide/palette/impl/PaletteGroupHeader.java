package com.intellij.ide.palette.impl;

import com.intellij.ide.palette.PaletteGroup;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * @author yole
 */
public class PaletteGroupHeader extends JCheckBox {
  private PaletteComponentList myComponentList;

  public PaletteGroupHeader(PaletteGroup group) {
    setText(group.getName());
    setSelected(true);
    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myComponentList != null) {
          myComponentList.setVisible(isSelected());
        }
      }
    });

    setIcon(UIManager.getIcon("Tree.collapsedIcon"));
    setSelectedIcon(UIManager.getIcon("Tree.expandedIcon"));
    setFont(getFont().deriveFont(Font.BOLD));
    setFocusPainted(false);
    setMargin(new Insets(0, 3, 0, 3));
    if (getBorder() instanceof CompoundBorder) { // from BasicLookAndFeel
      Dimension pref = getPreferredSize();
      pref.height -= 3;
      setPreferredSize(pref);
    }

    initActions();
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

  private class MoveFocusAction extends AbstractAction {
    private boolean moveDown;

    public MoveFocusAction(boolean moveDown) {
      this.moveDown = moveDown;
    }

    public void actionPerformed(ActionEvent e) {
      KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      Container container = kfm.getCurrentFocusCycleRoot();
      FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
      if (null == policy) policy = kfm.getDefaultFocusTraversalPolicy();
      Component next =
        moveDown ? policy.getComponentAfter(container, PaletteGroupHeader.this) : policy.getComponentBefore(container, PaletteGroupHeader.this);
      if (null != next && next instanceof PaletteComponentList) {
        final PaletteComponentList list = (PaletteComponentList)next;
        if (list.getModel().getSize() != 0) {
          list.takeFocusFrom(PaletteGroupHeader.this, list == myComponentList ? 0 : -1);
          return;
        }
        else {
          next = moveDown ? policy.getComponentAfter(container, next) : policy.getComponentBefore(container, next);
        }
      }
      if (null != next && next instanceof PaletteGroupHeader) {
        next.requestFocus();
      }
    }
  }

  private class ExpandAction extends AbstractAction {
    private boolean expand;

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
