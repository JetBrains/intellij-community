package com.intellij.xdebugger.impl.frame;

import com.intellij.openapi.Disposable;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel;
import com.intellij.xdebugger.impl.ui.tree.nodes.MessageTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class XDebugWatchesView extends XDebugViewBase {
  private XDebuggerTreePanel myTreePanel;
  private List<String> myWatchExpressions = new ArrayList<String>();

  public XDebugWatchesView(final XDebugSession session, final Disposable parentDisposable) {
    super(session, parentDisposable);
    myTreePanel = new XDebuggerTreePanel(session, session.getDebugProcess().getEditorsProvider(), null,
                                         XDebuggerActions.WATCHES_TREE_POPUP_GROUP);
  }

  public void addWatchExpression(@NotNull String expression) {
    myWatchExpressions.add(expression);
    XStackFrame stackFrame = mySession.getCurrentStackFrame();
    if (stackFrame != null) {
      XDebuggerEvaluator evaluator = stackFrame.getEvaluator();
      XDebuggerTreeNode root = myTreePanel.getTree().getRoot();
      if (evaluator != null && root instanceof WatchesRootNode) {
        ((WatchesRootNode)root).addWatchExpression(evaluator, expression);
      }
    }
  }

  protected void rebuildView(final boolean onlyFrameChanged) {
    XStackFrame stackFrame = mySession.getCurrentStackFrame();
    XDebuggerTree tree = myTreePanel.getTree();
    if (stackFrame != null) {
      tree.setSourcePosition(stackFrame.getSourcePosition());
      tree.setRoot(new WatchesRootNode(tree, myWatchExpressions, stackFrame.getEvaluator()), false);
    }
    else {
      tree.setSourcePosition(null);
      tree.setRoot(MessageTreeNode.createInfoMessage(tree, null, mySession.getDebugProcess().getCurrentStateMessage()), true);
    }
  }

  public XDebuggerTree getTree() {
    return myTreePanel.getTree();
  }

  public JPanel getMainPanel() {
    return myTreePanel.getMainPanel();
  }

  public void removeWatches(final List<WatchNode> watchNodes) {
    XDebuggerTreeNode root = getTree().getRoot();
    if (!(root instanceof WatchesRootNode)) return;
    final WatchesRootNode watchesRoot = (WatchesRootNode)root;

    for (WatchNode watchNode : watchNodes) {
      myWatchExpressions.remove(watchNode.getExpression());
    }

    watchesRoot.removeChildren(watchNodes);
  }
}
