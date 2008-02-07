package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.Tree;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeRoot;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;

/**
 * @author nik
 */
public class XDebuggerTree extends Tree implements DataProvider {
  public static final DataKey<XDebuggerTree> XDEBUGGER_TREE_KEY = DataKey.create("xdebugger.tree");
  private DefaultTreeModel myTreeModel;
  private final Project myProject;
  private final XDebuggerEditorsProvider myEditorsProvider;
  private final XSourcePosition mySourcePosition;

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

  public <Root extends XDebuggerTreeNode & XDebuggerTreeRoot> void setRoot(Root node) {
    myTreeModel.setRoot(node);
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

  public void rebuild() {
    XDebuggerTreeRoot root = (XDebuggerTreeRoot)myTreeModel.getRoot();
    root.rebuildNodes();
  }
}
