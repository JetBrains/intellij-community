package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class WatchesRootNode extends XValueContainerNode<WatchesRootNode.WatchItemContainer> {
  public WatchesRootNode(final XDebuggerTree tree, List<String> expressions, XDebuggerEvaluator evaluator) {
    super(tree, null, new WatchItemContainer(expressions, evaluator));
    setLeaf(false);
  }

  protected MessageTreeNode createLoadingMessageNode() {
    return MessageTreeNode.createEvaluatingMessage(myTree, this);
  }

  protected XValueContainerNode<?> createChildNode(final XValue child) {
    return new WatchNode(myTree, this, child);
  }

  public List<String> getExpressions() {
    return myValueContainer.getExpressions();
  }

  public static class WatchItemContainer extends XValueContainer {
    private final List<String> myExpressions;
    private final XDebuggerEvaluator myEvaluator;
    private int myCurrentExpression;

    public WatchItemContainer(final List<String> expressions, final XDebuggerEvaluator evaluator) {
      myExpressions = expressions;
      myEvaluator = evaluator;
      myCurrentExpression = 0;
    }

    //todo[nik] synchronize?
    public void computeChildren(@NotNull final XCompositeNode node) {
      if (myCurrentExpression >= myExpressions.size()) {
        node.addChildren(Collections.<XValue>emptyList(), true);
        return;
      }
      String expression = myExpressions.get(myCurrentExpression);
      myCurrentExpression++;
      myEvaluator.evaluate(expression, new XDebuggerEvaluator.XEvaluationCallback() {
        public void evaluated(@NotNull final XValue result) {
          node.addChildren(Collections.singletonList(result), false);
          computeChildren(node);
        }

        public void errorOccured(@NotNull final String errorMessage) {
          node.setErrorMessage(errorMessage);
        }
      });
    }

    public List<String> getExpressions() {
      return myExpressions;
    }

  }
}
