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

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.io.IOException;

/** Handles drop actions associated with manual rearrangement. */
public class TreeDropTarget
  implements DropTargetListener
{
  private static final Logger LOG                = Logger.getInstance("#" + TreeDropTarget.class.getName());
  private static final Color  xorBackgroundColor = new Color(255 - 8, 255 - 36, 255 - 107);
  DropTarget      target;
  PopupTree       targetTree;
  ILiveRearranger lrp;
  /** Cached information about the location of the last insertion row to be drawn. */
  private transient Rectangle lastRowRect;

  /** cached value of last row to be drawn. */
  private           int     lastRow;
  /**
   * Cached information about whether insertion was possible at the last
   * insertion point.
   */
  private transient boolean canInsert;

  /** true if rectangle has been drawn. */
  private boolean rectangleDrawn;
  /** row being dragged. */
  private int     srcRow;

  public TreeDropTarget(PopupTree tree,
                        ILiveRearranger lrp)
  {
    this.lrp = lrp;
    this.targetTree = tree;
    target = new DropTarget(targetTree, DnDConstants.ACTION_MOVE, this);
    this.lastRowRect = new Rectangle();
    this.canInsert = false;
    rectangleDrawn = false;
    srcRow = -1;
    tree.setTreeDropTarget(this);
  }

  public void dragEnter(DropTargetDragEvent dtde) {
//        logger.debug("tgt dragEnter calling dragOver logic");
    dragOver(dtde);
  }

  public int getSrcRow() {
    return srcRow;
  }

  public void setSrcRow(int srcRow) {
    this.srcRow = srcRow;
  }

  public void dragOver(DropTargetDragEvent dtde) {
//        logger.debug("target dragOver: src drop actions=" + dtde.getSourceActions() +
//                ", src drop action=" + dtde.getDropAction());
    clearInsertionPoint();
    Point pt = dtde.getLocation();
    lastRow = targetTree.getClosestRowForLocation(pt.x, pt.y);

    computeInsertionPoint(lastRow);
    TreePath path = targetTree.getClosestPathForLocation(pt.x, pt.y);
    final DefaultMutableTreeNode tgtNode;
    tgtNode = (DefaultMutableTreeNode)path.getLastPathComponent();
//        logger.debug("tgt dragOver, closest node=" + tgtNode);
    // compare selected node(s) to see if it has same parent as tgtNode.
    final DefaultMutableTreeNode srcNode;
    final TreePath srcPath = targetTree.getSelectionPath();
    if (srcPath != null) {
      srcNode = (DefaultMutableTreeNode)srcPath.getLastPathComponent();
//            logger.debug("tgt dragOver, selected node=" + srcNode);
      if (srcNode.getParent() == tgtNode.getParent() &&
          srcNode != tgtNode)
      {
//                logger.debug("tgt dragOver: accept drag");
        canInsert = true;
        drawInsertionPoint();
        dtde.acceptDrag(DnDConstants.ACTION_MOVE);
        return;
      }
    }
//        logger.debug("tgt dragOver: reject drag");
    canInsert = false;
    dtde.rejectDrag();
  }

  public void dropActionChanged(DropTargetDragEvent dtde) {
//        logger.debug("tgt:dropActionChanged, action=" + dtde.getDropAction());
  }

  public void drop(DropTargetDropEvent dtde) {
    clearInsertionPoint();
    canInsert = false;
    // figure out where the drop occurred.
    Point pt = dtde.getLocation();
    TreePath path = targetTree.getClosestPathForLocation(pt.x, pt.y);
    Transferable t = dtde.getTransferable();
    DataFlavor[] flavors = t.getTransferDataFlavors();
    for (DataFlavor flavor : flavors) {
      if (t.isDataFlavorSupported(flavor)) {
        DefaultMutableTreeNode dstNode = (DefaultMutableTreeNode)path.getLastPathComponent();
        Transferable tx = dtde.getTransferable();
        TreePath[] tr = null;
        try {
          Object o = tx.getTransferData(TransferableTreeNode.treePathFlavor);
          tr = (TreePath[])o;
        }
        catch (UnsupportedFlavorException e) {
          e.printStackTrace();
          dtde.rejectDrop();
          return;
        }
        catch (IOException e) {
          e.printStackTrace();
          dtde.rejectDrop();
          return;
        }
        if (tr == null) {
          dtde.rejectDrop();
          return;
        }
        final DefaultMutableTreeNode srcNode = (DefaultMutableTreeNode)tr[0].getLastPathComponent();
        int srcIndex = targetTree.haveCommonAncestors(srcNode, dstNode);
        if (srcIndex < 0) {
          LOG.debug(
            "reject drop onto " +
            dstNode +
            "; parent of " +
            srcNode + " is different"
          );
          dtde.rejectDrop();
          return;
        }
        else {
          DefaultMutableTreeNode parent = (DefaultMutableTreeNode)dstNode.getParent();
          DefaultMutableTreeNode srcInCurrentTree = (DefaultMutableTreeNode)parent.getChildAt(srcIndex);
          int dstRow = parent.getIndex(dstNode);
          boolean before = true;
          if (srcIndex < dstRow) {
            LOG.debug(
              "drop: move " +
              srcIndex +
              ":" +
              srcInCurrentTree +
              " after " + dstRow + ":" + dstNode
            );
            before = false;
          }
          else if (srcIndex == dstRow) {
            LOG.debug("reject drop: item onto itself");
            dtde.rejectDrop();
            return;
          }
          else {
            LOG.debug(
              "drop: move " +
              srcIndex +
              ":" +
              srcInCurrentTree +
              " before " + dstRow + ":" + dstNode
            );
          }
          targetTree.moveSelection(dstRow, before);
          lrp.setRearrangementOccurred(true);
          dtde.acceptDrop(dtde.getDropAction());
          dtde.dropComplete(true);
          return;
        }
      }
    }
    dtde.rejectDrop();
  }

  public void dragExit(DropTargetEvent dte) {
    LOG.debug("tgt dragExit");
    clearInsertionPoint();
    canInsert = false;
  }

  public void clearInsertionPoint() {
    if (rectangleDrawn) {
      // Erase the old insertion point by drawing over it.
      drawInsertionPoint();
    }
  }

  /**
   * Draw or erase the current insertion point indicator. This draws in XOR
   * mode, so drawing the indicator once will display it and drawing it again
   * in the same location will erase it.
   */
  public void drawInsertionPoint() {
    if (canInsert) {
      Graphics g = this.targetTree.getGraphics();
      g.setXORMode(xorBackgroundColor);
      g.drawRect(
        this.lastRowRect.x, this.lastRowRect.y,
        this.lastRowRect.width, this.lastRowRect.height
      );
      rectangleDrawn = !rectangleDrawn;
    }
  }

  public void computeInsertionPoint() {
    computeInsertionPoint(lastRow);
  }

  /**
   * Given a particular point over the associated tree component, determine
   * the corresponding insertion point in the tree for dropped data. The tree
   * path that the insertion will be relative to can be found using the
   * <code>JTree</code> method <code>getClosestPathForLocation</code>. The
   * insertion itself will either be before, after, or in the node at the end
   * of the path. This method also calculates the rectangle which can be used
   * to draw the appropriate indicator for the insertion point.
   *
   * @param row the row number of the tree corresponding to the drop target.
   */
  public void computeInsertionPoint(int row) {
    JTree tree = targetTree;

    Rectangle rowBounds = tree.getRowBounds(row);
    if (row < srcRow) {
      // Insertion point is immediately before the node.
      this.lastRowRect.setBounds(rowBounds.x, rowBounds.y, rowBounds.width, 0);
    }
    else if (row > srcRow) {
      // Insertion point is immediately after the node.
      this.lastRowRect.setBounds(
        rowBounds.x, rowBounds.y + rowBounds.height - 1,
        rowBounds.width, 0
      );
    }
    else {
      // Insertion point is on the node - dropped data will be appended to the
      // nodes child list.  Draw nothing in this case.
      this.lastRowRect.setBounds(rowBounds.x, rowBounds.y, 0, 0);
    }
  }
}
