package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.frame.XStackFrame;

/**
 * @author nik
 */
public class XStackFrameNode extends XDebuggerTreeNode<XStackFrame> {
  public XStackFrameNode(final XStackFrame xStackFrame) {
    super(null, xStackFrame);
  }
}
