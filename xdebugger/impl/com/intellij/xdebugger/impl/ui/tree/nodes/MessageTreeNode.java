package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerBundle;
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
  private boolean myEllipsis;

  private MessageTreeNode(XDebuggerTree tree, final XDebuggerTreeNode parent, final String message, final SimpleTextAttributes attributes,
                          @Nullable Icon icon) {
    this(tree, parent, message, attributes, icon, false);
  }

  private MessageTreeNode(XDebuggerTree tree, final XDebuggerTreeNode parent, final String message, final SimpleTextAttributes attributes, @Nullable Icon icon,
                          final boolean ellipsis) {
    super(tree, parent, true);
    myEllipsis = ellipsis;
    setIcon(icon);
    myText.append(message, attributes);
  }

  protected List<? extends TreeNode> getChildren() {
    return Collections.emptyList();
  }

  public boolean isEllipsis() {
    return myEllipsis;
  }

  public List<? extends XDebuggerTreeNode> getLoadedChildren() {
    return null;
  }

  public static MessageTreeNode createEllipsisNode(XDebuggerTree tree, XDebuggerTreeNode parent, final int remaining) {
    String message = remaining == -1 ? "..." : XDebuggerBundle.message("node.text.ellipsis.0.more.nodes.double.click.to.show", remaining);
    return new MessageTreeNode(tree, parent, message, SimpleTextAttributes.REGULAR_ATTRIBUTES, null, true);
  }

  public static MessageTreeNode createMessageNode(XDebuggerTree tree, XDebuggerTreeNode parent, String message, @Nullable Icon icon) {
    return new MessageTreeNode(tree, parent, message, SimpleTextAttributes.REGULAR_ATTRIBUTES, icon);
  }

  public static MessageTreeNode createLoadingMessage(XDebuggerTree tree, final XDebuggerTreeNode parent) {
    return new MessageTreeNode(tree, parent, XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES, null);
  }

  public static MessageTreeNode createEvaluatingMessage(XDebuggerTree tree, final XDebuggerTreeNode parent, final String message) {
    return new MessageTreeNode(tree, parent, message, XDebuggerUIConstants.EVALUATING_EXPRESSION_HIGHLIGHT_ATTRIBUTES, null);
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
