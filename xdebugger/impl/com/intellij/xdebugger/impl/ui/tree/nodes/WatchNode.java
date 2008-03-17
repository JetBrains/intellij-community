package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.frame.XValue;

/**
 * @author nik
 */
public class WatchNode extends XValueNodeImpl {
  public WatchNode(final XDebuggerTree tree, final XDebuggerTreeNode parent, final XValue value) {
    super(tree, parent, value);
  }
}
