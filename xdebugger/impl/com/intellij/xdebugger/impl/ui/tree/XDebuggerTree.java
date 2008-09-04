package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.Tree;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author nik
 */
public class XDebuggerTree extends Tree implements DataProvider {
  private static final DataKey<XDebuggerTree> XDEBUGGER_TREE_KEY = DataKey.create("xdebugger.tree");
  private DefaultTreeModel myTreeModel;
  private final Project myProject;
  private final XDebuggerEditorsProvider myEditorsProvider;
  private XSourcePosition mySourcePosition;
  private final List<XDebuggerTreeListener> myListeners = new CopyOnWriteArrayList<XDebuggerTreeListener>();
  private final XDebugSession mySession;

  public XDebuggerTree(final @NotNull XDebugSession session, final XDebuggerEditorsProvider editorsProvider, final XSourcePosition sourcePosition) {
    mySession = session;
    myProject = session.getProject();
    myEditorsProvider = editorsProvider;
    mySourcePosition = sourcePosition;
    myTreeModel = new DefaultTreeModel(null);
    setModel(myTreeModel);
    setCellRenderer(new XDebuggerTreeRenderer());
    setRootVisible(false);
    setShowsRootHandles(true);
  }

  public void addTreeListener(@NotNull XDebuggerTreeListener listener) {
    myListeners.add(listener);
  }

  public void removeTreeListener(@NotNull XDebuggerTreeListener listener) {
    myListeners.remove(listener);
  }

  public void setRoot(XDebuggerTreeNode root, final boolean rootVisible) {
    setRootVisible(rootVisible);
    myTreeModel.setRoot(root);
  }

  public XDebuggerTreeNode getRoot() {
    return (XDebuggerTreeNode)myTreeModel.getRoot();
  }

  @Nullable
  public XSourcePosition getSourcePosition() {
    return mySourcePosition;
  }

  public void setSourcePosition(final @Nullable XSourcePosition sourcePosition) {
    mySourcePosition = sourcePosition;
  }

  @NotNull
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myEditorsProvider;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public XDebugSession getSession() {
    return mySession;
  }

  public DefaultTreeModel getTreeModel() {
    return myTreeModel;
  }

  @Nullable
  public Object getData(@NonNls final String dataId) {
    if (XDEBUGGER_TREE_KEY.getName().equals(dataId)) {
      return this;
    }
    return null;
  }

  public void rebuildAndRestore(final XDebuggerTreeState treeState) {
    Object rootNode = myTreeModel.getRoot();
    if (rootNode instanceof XValueContainerNode<?>) {
      XValueContainerNode<?> root = (XValueContainerNode<?>)rootNode;
      root.clearChildren();
      treeState.restoreState(this);
      repaint();
    }
  }

  public void childrenLoaded(final @NotNull XDebuggerTreeNode node, final @NotNull List<XValueContainerNode<?>> children, final boolean last) {
    for (XDebuggerTreeListener listener : myListeners) {
      listener.childrenLoaded(node, children, last);
    }
  }

  public void nodeLoaded(final @NotNull XValueNodeImpl node, final @NotNull String name, final @NotNull String value) {
    for (XDebuggerTreeListener listener : myListeners) {
      listener.nodeLoaded(node, name, value);
    }
  }

  public void markNodesObsolete() {
    Object root = myTreeModel.getRoot();
    if (root instanceof XValueContainerNode<?>) {
      markNodesObsolete((XValueContainerNode<?>)root);
    }
  }

  private static void markNodesObsolete(final XValueContainerNode<?> node) {
    node.setObsolete();
    List<XValueContainerNode<?>> loadedChildren = node.getLoadedChildren();
    if (loadedChildren != null) {
      for (XValueContainerNode<?> child : loadedChildren) {
        markNodesObsolete(child);
      }
    }
  }

  @Nullable
  public static XDebuggerTree getTree(final AnActionEvent e) {
    return e.getData(XDEBUGGER_TREE_KEY);
  }

  @Nullable
  public static XDebuggerTree getTree(DataContext context) {
    return XDEBUGGER_TREE_KEY.getData(context);
  }
}
