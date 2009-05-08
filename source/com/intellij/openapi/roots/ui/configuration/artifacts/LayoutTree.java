package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.dnd.AdvancedDnDSource;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
* @author nik
*/
public class LayoutTree extends SimpleTree implements AdvancedDnDSource {
  private static final Convertor<TreePath, String> SPEED_SEARCH_CONVERTOR = new Convertor<TreePath, String>() {
    public String convert(final TreePath path) {
      Object o = path.getLastPathComponent();
      if (o instanceof ArtifactsTreeNode) {
        return ((PackagingElementNode)o).getPresentation().getSearchName();
      }
      return "";
    }
  };

  public LayoutTree(DefaultTreeModel treeModel) {
    super(treeModel);
  }

  @Override
  public String getToolTipText(final MouseEvent event) {
    TreePath path = getPathForLocation(event.getX(), event.getY());
    if (path != null) {
      return ((PackagingElementNode)path.getLastPathComponent()).getPresentation().getTooltipText();
    }
    return super.getToolTipText();
  }

  @Override
  protected void configureUiHelper(TreeUIHelper helper) {
    new TreeSpeedSearch(this, SPEED_SEARCH_CONVERTOR, true);
    helper.installToolTipHandler(this);
  }

  public boolean canStartDragging(DnDAction action, Point dragOrigin) {
    return false;
  }

  public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
    return null;
  }

  public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
    return null;
  }

  public void dragDropEnd() {
  }

  public void dropActionChanged(int gestureModifiers) {
  }

  public void processMouseEvent(MouseEvent e) {
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

  public void dispose() {
  }
}
