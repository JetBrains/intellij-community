package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.util.ui.TreeWithEmptyText;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

/**
 * @author yole
*/
public class TreeLinkMouseListener extends MouseAdapter implements MouseMotionListener {
  private final ColoredTreeCellRenderer myRenderer;
  protected DefaultMutableTreeNode myLastHitNode;

  public TreeLinkMouseListener(final ColoredTreeCellRenderer renderer) {
    myRenderer = renderer;
  }

  public void mouseClicked(final MouseEvent e) {
    if (!e.isPopupTrigger() && e.getButton() == 1) {
      Object tag = getTagAt(e);
      handleTagClick(tag);
    }
  }

  protected void handleTagClick(final Object tag) {
    if (tag instanceof Runnable) {
      ((Runnable) tag).run();
    }
  }

  protected void showTooltip(final JTree tree, final MouseEvent e, final HaveTooltip launcher) {
    final String text = tree.getToolTipText(e);
    final String newText = launcher == null ? null : launcher.getTooltip();
    if (! Comparing.equal(text, newText)) {
      tree.setToolTipText(newText);
    }
  }

  @Nullable
  private Object getTagAt(final MouseEvent e) {
    JTree tree = (JTree) e.getSource();
    Object tag = null;
    HaveTooltip haveTooltip = null;
    final TreePath path = tree.getPathForLocation(e.getX(), e.getY());
    if (path != null) {
      final Rectangle rectangle = tree.getPathBounds(path);
      int dx = e.getX() - rectangle.x;
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
      if (myLastHitNode != treeNode) {
        myLastHitNode = treeNode;
        myRenderer.getTreeCellRendererComponent(tree, treeNode, false, false, treeNode.isLeaf(), -1, false);
      }
      int i = myRenderer.findFragmentAt(dx);
      if (i >= 0) {
        tag = myRenderer.getFragmentTag(i);
        if (treeNode instanceof HaveTooltip) {
          haveTooltip = (HaveTooltip) treeNode;
        }
      }
    }
    showTooltip(tree, e, haveTooltip);
    return tag;
  }

  public void mouseDragged(MouseEvent e) {
  }

  public void mouseMoved(MouseEvent e) {
    JTree tree = (JTree) e.getSource();
    if (tree instanceof TreeWithEmptyText && ((TreeWithEmptyText) tree).isModelEmpty()) return;
    Object tag = getTagAt(e);
    if (tag != null) {
      tree.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    else {
      tree.setCursor(Cursor.getDefaultCursor());
    }
  }

  public void install(final JTree tree) {
    tree.addMouseListener(this);
    tree.addMouseMotionListener(this);
  }

  public static class BrowserLauncher implements Runnable {
    private final String myUrl;

    public BrowserLauncher(final String url) {
      myUrl = url;
    }

    public void run() {
      BrowserUtil.launchBrowser(myUrl);
    }
  }

  public interface HaveTooltip {
    String getTooltip();
  }
}