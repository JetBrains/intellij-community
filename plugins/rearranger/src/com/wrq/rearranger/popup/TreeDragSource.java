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
import java.awt.*;
import java.awt.dnd.*;

/** Contains logic to start a drag from the popup file structure tree. */
public class TreeDragSource
  implements DragSourceListener,
             DragGestureListener
{
  private static final Logger LOG = Logger.getInstance("#" + TreeDragSource.class.getName());
  DragSource            source;
  DragGestureRecognizer recognizer;
  TransferableTreeNode  transferable;
  JTree                 sourceTree;
  TreeDropTarget        tdt;

  public TreeDragSource(JTree sourceTree, int actions, TreeDropTarget tdt) {
    LOG.debug("construct TreeDragSource, actions=" + actions);
    this.sourceTree = sourceTree;
    this.tdt = tdt;
    source = DragSource.getDefaultDragSource();
    recognizer = source.createDefaultDragGestureRecognizer(
      sourceTree, actions, this);
  }

  public void dragEnter(DragSourceDragEvent dsde) {
    LOG.debug("src dragEnter, calling dragOver");
    dragOver(dsde);
  }

  public void dragOver(DragSourceDragEvent dsde) {
//        logger.debug("src dragOver; drop action=" + dsde.getDropAction() +
//                ", target actions=" + dsde.getTargetActions());
//        dsde.getDragSourceContext().setCursor(dsde.getDropAction() > 0
//                ? DragSource.DefaultMoveDrop
//                : DragSource.DefaultMoveNoDrop);
  }

  public void dropActionChanged(DragSourceDragEvent dsde) {
    LOG.debug("src dropActionChanged: action=" + dsde.getDropAction());
  }

  public void dragDropEnd(DragSourceDropEvent dsde) {
    LOG.debug("src dragDropEnd, success=" + dsde.getDropSuccess());
  }

  public void dragExit(DragSourceEvent dse) {
//        logger.debug("src dragExit");
  }

  public void dragGestureRecognized(DragGestureEvent dge) {
    LOG.debug("src dragGestureRecognized, action=" + dge.getDragAction() +
              ", n paths in selection=" + sourceTree.getSelectionPaths().length);
    final Point point = dge.getDragOrigin();
//        TreePath path = sourceTree.getSelectionPath();
//        if (path == null ||
//                path.getPathCount() <= 1)
//        {
//            // can't move the root node or an empty selection
//            System.out.println("can't drag root node or empty selection");
//            return;
//        }
    // Make a version of the node that we can use for DnD.
    tdt.setSrcRow(sourceTree.getClosestRowForLocation(point.x, point.y));
    transferable = new TransferableTreeNode(sourceTree.getSelectionPaths());
    dge.startDrag(null, transferable, this);
  }
}
