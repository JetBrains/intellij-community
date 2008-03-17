package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.evaluate.EvaluationDialogBase;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author nik
 */
public class EvaluatingExpressionRootNode extends XValueContainerNode<EvaluatingExpressionRootNode.EvaluatingResultContainer> {
  public EvaluatingExpressionRootNode(EvaluationDialogBase evaluationDialog, final XDebuggerTree tree) {
    super(tree, null, new EvaluatingResultContainer(evaluationDialog));
    setLeaf(false);
  }

  protected MessageTreeNode createLoadingMessageNode() {
    return MessageTreeNode.createEvaluatingMessage(myTree, this);
  }

  public static class EvaluatingResultContainer extends XValueContainer {
    private final EvaluationDialogBase myDialog;

    public EvaluatingResultContainer(final EvaluationDialogBase dialog) {
      myDialog = dialog;
    }

    public void computeChildren(@NotNull final XCompositeNode node) {
      myDialog.startEvaluation(new XDebuggerEvaluator.XEvaluationCallback() {
        public void evaluated(@NotNull final XValue result) {
          node.addChildren(Collections.singletonList(result), true);
        }

        public void errorOccured(@NotNull final String errorMessage) {
          node.setErrorMessage(errorMessage);
        }
      });
    }
  }
}
