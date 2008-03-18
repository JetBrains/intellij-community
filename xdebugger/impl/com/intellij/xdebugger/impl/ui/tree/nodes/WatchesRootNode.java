package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.frame.WatchInplaceEditor;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class WatchesRootNode extends XDebuggerTreeNode {
  private List<XDebuggerTreeNode> myChildren;
  private List<XDebuggerTreeNode> myLoadedChildren;

  public WatchesRootNode(final XDebuggerTree tree, List<String> expressions, XDebuggerEvaluator evaluator) {
    super(tree, null, false);

    myChildren = new ArrayList<XDebuggerTreeNode>();
    for (String expression : expressions) {
      myChildren.add(MessageTreeNode.createEvaluatingMessage(tree, this, expression + " = ..."));
    }

    for (int i = 0; i < expressions.size(); i++) {
      String expression = expressions.get(i);
      evaluator.evaluate(expression, new MyEvaluationCallback(expression, myChildren.get(i)));
    }
  }

  protected List<? extends TreeNode> getChildren() {
    return myChildren;
  }

  public List<? extends XDebuggerTreeNode> getLoadedChildren() {
    if (myLoadedChildren == null) {
      myLoadedChildren = new ArrayList<XDebuggerTreeNode>();
      for (XDebuggerTreeNode child : myChildren) {
        if (child instanceof WatchNode) {
          myLoadedChildren.add(child);
        }
      }
    }
    return myLoadedChildren;
  }

  private void replaceNode(final XDebuggerTreeNode oldNode, final XDebuggerTreeNode newNode) {
    for (int i = 0; i < myChildren.size(); i++) {
      XDebuggerTreeNode child = myChildren.get(i);
      if (child == oldNode) {
        myChildren.set(i, newNode);
        if (newNode instanceof XValueContainerNode<?>) {
          myLoadedChildren = null;
          myTree.childrenLoaded(this, Collections.<XValueContainerNode<?>>singletonList((XValueContainerNode<?>)newNode), false);
        }
        fireNodeChildrenChanged();
        return;
      }
    }
  }

  public void addWatchExpression(final XDebuggerEvaluator evaluator, final String expression) {
    MessageTreeNode message = MessageTreeNode.createEvaluatingMessage(myTree, this, expression + "...");
    myChildren.add(message);
    evaluator.evaluate(expression, new MyEvaluationCallback(expression, message));
    fireNodeChildrenChanged();
  }

  public void removeChildNode(XDebuggerTreeNode node) {
    myChildren.remove(node);
    myLoadedChildren = null;
    fireNodeChildrenChanged();
  }

  public void removeChildren(Collection<? extends XDebuggerTreeNode> nodes) {
    myChildren.removeAll(nodes);
    myLoadedChildren = null;
    fireNodeChildrenChanged();
  }

  public void addNewWatch() {
    MessageTreeNode node = MessageTreeNode.createMessageNode(myTree, this, "", null);
    myChildren.add(node);
    fireNodeChildrenChanged();
    WatchInplaceEditor editor = new WatchInplaceEditor(this, node, "watch");
    editor.show();
  }

  private class MyEvaluationCallback implements XDebuggerEvaluator.XEvaluationCallback {
    private final String myExpression;
    private final XDebuggerTreeNode myResultPlace;

    public MyEvaluationCallback(final String expression, final XDebuggerTreeNode resultPlace) {
      myExpression = expression;
      myResultPlace = resultPlace;
    }

    public void evaluated(@NotNull final XValue result) {
      DebuggerUIUtil.invokeLater(new Runnable() {
        public void run() {
          replaceNode(myResultPlace, new WatchNode(myTree, WatchesRootNode.this, result, myExpression));
        }
      });
    }

    public void errorOccured(@NotNull final String errorMessage) {
      DebuggerUIUtil.invokeLater(new Runnable() {
        public void run() {
          replaceNode(myResultPlace, MessageTreeNode.createErrorMessage(myTree, WatchesRootNode.this, errorMessage));
        }
      });
    }
  }
}
