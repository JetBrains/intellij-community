package com.intellij.xdebugger.impl.evaluate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.XDebuggerEditorBase;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel;
import com.intellij.xdebugger.impl.ui.tree.nodes.MessageTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeRoot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class EvaluationDialogBase extends DialogWrapper {
  private JPanel myMainPanel;
  private JPanel myResultPanel;
  private JPanel myInputPanel;
  private XDebuggerTreePanel myTreePanel;
  private final XSuspendContext mySuspendContext;

  protected EvaluationDialogBase(@NotNull Project project, String title, final XDebuggerEditorsProvider editorsProvider, final XSuspendContext suspendContext,
                                 final XSourcePosition sourcePosition) {
    super(project, true);
    mySuspendContext = suspendContext;
    setModal(false);
    setTitle(title);
    setOKButtonText(XDebuggerBundle.message("xdebugger.button.evaluate"));
    setCancelButtonText(XDebuggerBundle.message("xdebugger.evaluate.dialog.close"));
    myTreePanel = new XDebuggerTreePanel(project, editorsProvider, sourcePosition, XDebuggerActions.EVALUATE_DIALOG_POPUP_GROUP);
    myResultPanel.add(myTreePanel.getMainPanel(), BorderLayout.CENTER);
    init();
  }

  protected JPanel getInputPanel() {
    return myInputPanel;
  }

  protected JPanel getResultPanel() {
    return myResultPanel;
  }

  protected void doOKAction() {
    evaluate();
  }

  protected void evaluate() {
    final XDebuggerTree tree = myTreePanel.getTree();
    final EvaluatingExpressionRootNode root = new EvaluatingExpressionRootNode(tree);
    tree.setRoot(root);
    root.rebuildNodes();
    myResultPanel.invalidate();
    getInputEditor().selectAll();
  }

  protected void dispose() {
    myTreePanel.dispose();
    super.dispose();
  }

  protected abstract XDebuggerEditorBase getInputEditor();

  protected abstract void startEvaluation(XDebuggerEvaluator.XEvaluationCallback callback);

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  protected XDebuggerEvaluator getEvaluator() {
    return mySuspendContext.getEvaluator();
  }

  public JComponent getPreferredFocusedComponent() {
    return getInputEditor().getPreferredFocusedComponent();
  }

  public class EvaluatingExpressionRootNode extends XDebuggerTreeNode implements XDebuggerTreeRoot {
    private XDebuggerTreeNode myChild;

    public EvaluatingExpressionRootNode(XDebuggerTree tree) {
      super(tree, null, false);
      rebuildNodes();
    }

    protected List<? extends TreeNode> getChildren() {
      return Collections.singletonList(myChild);
    }

    public void rebuildNodes() {
      myChild = MessageTreeNode.createEvaluatingMessage(myTree, this);
      fireNodeChildrenChanged();
      startEvaluation(new XDebuggerEvaluator.XEvaluationCallback() {
        public void evaluated(@NotNull final XValue result) {
          DebuggerUIUtil.invokeLater(new Runnable() {
            public void run() {
              setChild(new XValueNodeImpl(myTree, EvaluatingExpressionRootNode.this, result));
            }
          });
        }

        public void errorOccured(@NotNull final String errorMessage) {
          DebuggerUIUtil.invokeLater(new Runnable() {
            public void run() {
              setChild(MessageTreeNode.createErrorMessage(myTree, EvaluatingExpressionRootNode.this, errorMessage));
            }
          });
        }
      });
    }

    public void setChild(final XDebuggerTreeNode node) {
      myChild = node;
      fireNodeChildrenChanged();
    }
  }
}
