package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.Tree;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
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
  public static final DataKey<XDebuggerTree> XDEBUGGER_TREE_KEY = DataKey.create("xdebugger.tree");
  private DefaultTreeModel myTreeModel;
  private final Project myProject;
  private final XDebuggerEditorsProvider myEditorsProvider;
  private final XSourcePosition mySourcePosition;
  private final List<XDebuggerTreeListener> myListeners = new CopyOnWriteArrayList<XDebuggerTreeListener>();

  public XDebuggerTree(final @NotNull Project project, final XDebuggerEditorsProvider editorsProvider, final XSourcePosition sourcePosition) {
    myProject = project;
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

  public void setRoot(XValueContainerNode<?> root) {
    myTreeModel.setRoot(root);
  }

  @Nullable 
  public XSourcePosition getSourcePosition() {
    return mySourcePosition;
  }

  @NotNull
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myEditorsProvider;
  }

  @NotNull
  public Project getProject() {
    return myProject;
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

  public void rebuildAndRestore(final DebuggerTreeState treeState) {
    XValueContainerNode<?> root = (XValueContainerNode<?>)myTreeModel.getRoot();
    root.clearChildren();
    treeState.restoreState(this);
    repaint();
  }

  public void childrenLoaded(final @NotNull XValueContainerNode<?> node, final @NotNull List<XValueContainerNode<?>> children) {
    for (XDebuggerTreeListener listener : myListeners) {
      listener.childrenLoaded(node, children);
    }
  }

  public void nodeLoaded(final @NotNull XValueNodeImpl node, final @NotNull String name, final @NotNull String value) {
    for (XDebuggerTreeListener listener : myListeners) {
      listener.nodeLoaded(node, name, value);
    }
  }
}
