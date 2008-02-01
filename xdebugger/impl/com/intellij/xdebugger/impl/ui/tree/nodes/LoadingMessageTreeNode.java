package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;

import javax.swing.tree.TreeNode;
import java.util.List;
import java.util.Collections;

/**
 * @author nik
 */
public class LoadingMessageTreeNode extends TreeNodeBase {
  public LoadingMessageTreeNode(final TreeNodeBase parent) {
    super(parent, true);
    myText.append(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES);
  }

  protected List<? extends TreeNode> getChildren() {
    return Collections.emptyList();
  }
}
