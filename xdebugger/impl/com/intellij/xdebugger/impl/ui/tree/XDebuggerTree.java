package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.util.ui.Tree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XStackFrameNode;
import com.intellij.xdebugger.frame.XStackFrame;

import javax.swing.tree.DefaultTreeModel;

/**
 * @author nik
 */
public class XDebuggerTree extends Tree {
  public XDebuggerTree(XStackFrame stackFrame) {
    setCellRenderer(new XDebuggerTreeRenderer());
    setRootVisible(false);
    setShowsRootHandles(true);
    setModel(new DefaultTreeModel(new XStackFrameNode(stackFrame)));
  }
}
