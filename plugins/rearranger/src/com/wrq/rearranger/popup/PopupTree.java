/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.popup;

import com.intellij.openapi.diagnostic.Logger;
import com.wrq.rearranger.entry.RangeEntry;
import com.wrq.rearranger.ruleinstance.RuleInstance;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.dnd.Autoscroll;
import java.util.LinkedList;

/** Handles vertical autoscrolling of the tree inside the live rearranger popup/dialog. */
public class PopupTree
  extends JTree
  implements Autoscroll
{
  private static final Logger LOG    = Logger.getInstance("#" + PopupTree.class.getName());
  private static final int    margin = 12;
  private       TreeDropTarget     treeDropTarget;
  private       boolean            exitedWithEnterKey;
  private final PopupTreeComponent owner;

  public PopupTree(TreeNode root, PopupTreeComponent owner) {
    super(root);
    this.owner = owner;
  }

  public void setTreeDropTarget(TreeDropTarget dropTarget) {
    treeDropTarget = dropTarget;
  }

  public boolean isExitedWithEnterKey() {
    return exitedWithEnterKey;
  }

  public void setExitedWithEnterKey(boolean exitedWithEnterKey) {
    this.exitedWithEnterKey = exitedWithEnterKey;
  }

  // scroll because mouse cursor is in the scroll zone.
  public void autoscroll(Point p) {
    // determine row.  Set y value to that of current row left edge; this allows scrolling to occur
    int realrow = getRowForLocation(p.x, p.y);
    if (treeDropTarget != null) {
      treeDropTarget.clearInsertionPoint();
      // Set y value to that of current row left edge; this allows scrolling to occur even when cursor is
      // to the right or left of the smallest rectangle enclosing the row.
      int row = treeDropTarget.getSrcRow();
      Rectangle r = getRowBounds(row);
      if (realrow < 0) {
        LOG.debug("autscroll: realrow < 0 for [" + p.x + "," + p.y + "], trying x=" + r.x);
        p.x = r.x;
        realrow = getRowForLocation(p.x, p.y);
      }
    }
    if (realrow < 0) {
      LOG.debug("autoscroll point=[" + p.x + "," + p.y + "] realrow=" + realrow);
      return;
    }
    Rectangle outer = getBounds();

    // now determine if the row is at the top or bottom of the screen.
    // Make the previous or next row visible accordingly.
    int newRow =
      p.y + outer.y <= margin
      ? realrow < 1 ? 0 : realrow - 1
      : realrow < getRowCount() - 1
        ? realrow + 1
        : realrow;
//        logger.setLevel(Level.DEBUG); -- following code doesn't work for horizontal scrolling; something interferes
//        int newCol = p.x;
//        if (this.getParent() instanceof JViewport)
//        {
//            JViewport parent = (JViewport) getParent();
//                logger.debug(
//                        "autoscroll horizontal TEST: p.x=" +
//                        p.x +
//                        ", bounds.x=" +
//                        outer.x +
//                        ", viewport x=" +
//                        parent.getBounds().x +
//                        ", width=" + parent.getBounds().width);
//            if (p.x + outer.x <= margin)
//            {
//                newCol = p.x - margin;
//                logger.debug(
//                        "autoscroll horizontal LEFT: new x=" + newCol
//                );
//                logger.debug("this=" + this.toString());
//                logger.debug("this.parent=" + this.getParent().toString());
//                Rectangle r = parent.getViewRect();
//                r.x -= margin;
//                parent.scrollRectToVisible(r);
//            }
//            else
//            {
//                if (p.x + outer.x + margin >= parent.getBounds().x + parent.getBounds().width)
//                {
//                    newCol = p.x + margin;
//                    logger.debug(
//                            "autoscroll horizontal RIGHT: new x=" + newCol
//                    );
////                    logger.debug("this=" + this.toString());
////                    logger.debug("this.parent=" + parent.toString());
//                    Rectangle r = parent.getViewRect();
//                    r.x += margin;
//                    parent.scrollRectToVisible(r);
//                }
//            }
//        }
    LOG.debug("autoscroll point=[" + p.x + "," + p.y + "] realrow=" + realrow + ", newRow=" + newRow);
    scrollRowToVisible(newRow);
    if (treeDropTarget != null) {
      treeDropTarget.computeInsertionPoint();
      treeDropTarget.drawInsertionPoint();
    }
  }

  /**
   * Calculate insets for the JTree, not the viewport the tree is in.
   * this makes it a bit messy.
   *
   * @return
   */
  public Insets getAutoscrollInsets() {
    Rectangle outer = getBounds();
    Rectangle inner = getParent().getBounds();
    return new Insets(
      inner.y - outer.y + margin,
      inner.x - outer.x + margin,
      outer.height - inner.height - inner.y + outer.y + margin,
      outer.width - inner.width - inner.x + outer.x + margin
    );
  }

  // Use this method to see the boundaries of the autoscroll active region.
//    public void paintComponent(Graphics g)
//    {
//        super.paintComponent(g);
//        Rectangle outer = getBounds();
//        Rectangle inner = getParent().getBounds();
//        g.setColor(Color.RED);
//        g.drawRect(-outer.x + margin, -outer.y + margin,
//                inner.width - 2*margin, inner.height - 2*margin);
//    }

  /**
   * Move the tree's selection to the destination row indicated.  The selection may contain multiple
   * discontiguous rows, but all rows must have the same parent node.
   *
   * @param dstIndex child index of common parent before which selection is to be inserted.  If null, insert at end of parent.
   */
  public void moveSelection(int dstIndex, boolean before) {
    /**
     * First, remove all mutable tree nodes from the tree's model and the rule instance.  Place them in a list.
     * If any are removed before the destination row, decrement the dstIndex so that the insertion still
     * happens in the correct place.
     */
    TreePath[] paths = getSelectionPaths();
    LinkedList<DefaultMutableTreeNode> nodeList = new LinkedList<DefaultMutableTreeNode>();
    LinkedList<RangeEntry> ruleList = new LinkedList<RangeEntry>();
    if (paths == null || paths.length == 0) {
      return; // nothing selected
    }
    owner.setRearrangementOccurred(true);
    final DefaultMutableTreeNode peerNode = (DefaultMutableTreeNode)paths[0].getLastPathComponent();
    RangeEntry entry = (RangeEntry)peerNode.getUserObject();
    DefaultMutableTreeNode peerParent = (DefaultMutableTreeNode)peerNode.getParent();
    DefaultMutableTreeNode dstNode = (DefaultMutableTreeNode)peerParent.getChildAt(dstIndex);
    if (!before && dstIndex < peerParent.getChildCount()) {
      dstIndex++;
    }
    RuleInstance ruleInstance = entry.getMatchedRule();
    DefaultTreeModel model = (DefaultTreeModel)getModel();

    for (TreePath path : paths) {
      DefaultMutableTreeNode srcNode = (DefaultMutableTreeNode)path.getLastPathComponent();
      DefaultMutableTreeNode srcParent = (DefaultMutableTreeNode)srcNode.getParent();
      int srcRow = srcParent.getIndex(srcNode);
      if (srcRow < dstIndex) {
        dstIndex--;
      }
      nodeList.add(srcNode);
      model.removeNodeFromParent(srcNode);
      RangeEntry match = (RangeEntry)srcNode.getUserObject();
      ruleInstance.getMatches().remove(match);
      ruleList.add(match);
    }
    /**
     * now insert all the nodes in the list at the appropriate spots in the model and rule instance.
     */
    clearSelection();
    TreePath visiblePath = null;
    /**
     * calculate the offset between the destination row in the model and its location in the ruleInstance's
     * list of matching items.  This is non-zero when fields exist but are not shown in the tree.
     */
    int dstOffset = (ruleInstance.getMatches().indexOf(dstNode.getUserObject())) - peerParent.getIndex(dstNode);
    while (!nodeList.isEmpty()) {
      DefaultMutableTreeNode srcNode = nodeList.removeFirst();
      model.insertNodeInto(srcNode, peerParent, dstIndex);
      final TreePath treePath = new TreePath(srcNode.getPath());
      addSelectionPath(treePath);
      if (before) {
        if (visiblePath == null) visiblePath = treePath;
      }
      else {
        visiblePath = treePath;
      }
      final RangeEntry match = ruleList.removeFirst();
      ruleInstance.getMatches().add(dstIndex + dstOffset, match);
      dstIndex++;
    }
    if (visiblePath != null) {
      scrollPathToVisible(visiblePath);
    }
  }

  /**
   * returns true if src and dst have common parent.  Since src has been created from serialized data,
   * the actual parents are different objects.  So compare by name up to the root.  Ignore any plain
   * DefaultMutableTreeNodes in the path (these correspond to rules) -- just compare names of
   * RearrangerTreeNodes.
   *
   * @param src RearrangerTreeNode reconstituted from serialized data
   * @param dst RearrangerTreeNode next to which src is to be dropped
   * @return -1 if src and dst do not have common parent; >= 0 to indicate position of src in children list
   */
  int haveCommonAncestors(DefaultMutableTreeNode src, DefaultMutableTreeNode dst) {
    if (src == null || dst == null) {
      return -1;
    }
    DefaultMutableTreeNode p = (DefaultMutableTreeNode)src.getParent();
    int result = p.getIndex(src);
    src = (DefaultMutableTreeNode)src.getParent();
    dst = (DefaultMutableTreeNode)dst.getParent();
    while (src != null && dst != null) {
      if (src instanceof RearrangerTreeNode) {
        if (dst instanceof RearrangerTreeNode) {
          RearrangerTreeNode s = (RearrangerTreeNode)src;
          RearrangerTreeNode d = (RearrangerTreeNode)dst;
          if (!s.name.equals(d.name)) {
            return -1;
          }
        }
        else {
          return -1;
        }
      }
      else if (dst instanceof RearrangerTreeNode) {
        return -1;
      }
      src = (DefaultMutableTreeNode)src.getParent();
      dst = (DefaultMutableTreeNode)dst.getParent();
    }
    return src == null && dst == null ? result : -1;
  }
}
