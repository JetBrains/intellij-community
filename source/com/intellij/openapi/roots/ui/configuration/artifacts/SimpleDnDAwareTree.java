package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.dnd.DnDAware;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author nik
 */
public class SimpleDnDAwareTree extends SimpleTree implements DnDAware {
  public void processMouseEvent(MouseEvent e) {
    if (getToolTipText() == null && e.getID() == MouseEvent.MOUSE_ENTERED) return;
    super.processMouseEvent(e);
  }

  public boolean isOverSelection(Point point) {
    return TreeUtil.isOverSelection(this, point);
  }

  public void dropSelectionButUnderPoint(Point point) {
    TreeUtil.dropSelectionButUnderPoint(this, point);
  }

  @NotNull
  public JComponent getComponent() {
    return this;
  }
}
