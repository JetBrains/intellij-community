package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class XValueNodeImpl extends XDebuggerTreeNode<XValue> implements XValueNode, XCompositeNode {
  public XValueNodeImpl(XDebuggerTree tree, final TreeNodeBase parent, final XValue value) {
    super(tree, parent, value);
    myText.append(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES);
    value.computePresentation(this);
  }

  public void setPresentation(@NotNull final String name, @Nullable final Icon icon, @Nullable final String type, @NotNull final String value,
                              final boolean hasChildren) {
    DebuggerUIUtil.invokeLater(new Runnable() {
      public void run() {
        setIcon(icon);
        myText.clear();
        myText.append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        myText.append(" = ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        myText.append(value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setLeaf(!hasChildren);
        fireNodeChanged();
      }
    });
  }
}
