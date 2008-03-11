package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class MessageTreeNode extends XDebuggerTreeNode {
  private MessageTreeNode(XDebuggerTree tree, final XDebuggerTreeNode parent, final String message, final SimpleTextAttributes attributes,
                          @Nullable Icon icon) {
    super(tree, parent, true);
    setIcon(icon);
    myText.append(message, attributes);
  }

  protected List<? extends TreeNode> getChildren() {
    return Collections.emptyList();
  }

  public static MessageTreeNode createLoadingMessage(XDebuggerTree tree, final XDebuggerTreeNode parent) {
    return new MessageTreeNode(tree, parent, XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES, null);
  }

  public static MessageTreeNode createEvaluatingMessage(XDebuggerTree tree, final XDebuggerTreeNode parent) {
    return new MessageTreeNode(tree, parent, XDebuggerUIConstants.EVALUATING_EXPRESSION_MESSAGE, XDebuggerUIConstants.EVALUATING_EXPRESSION_HIGHLIGHT_ATTRIBUTES, null);
  }

  public static MessageTreeNode createErrorMessage(XDebuggerTree tree, final XDebuggerTreeNode parent, @NotNull String errorMessage) {
    return new MessageTreeNode(tree, parent, errorMessage, XDebuggerUIConstants.ERROR_MESSAGE_ATTRIBUTES, XDebuggerUIConstants.ERROR_MESSAGE_ICON);
  }

  public static MessageTreeNode createInfoMessage(XDebuggerTree tree, final XDebuggerTreeNode parent, @NotNull String message) {
    return new MessageTreeNode(tree, parent, message, SimpleTextAttributes.REGULAR_ATTRIBUTES, XDebuggerUIConstants.INFORMATION_MESSAGE_ICON);
  }
}
