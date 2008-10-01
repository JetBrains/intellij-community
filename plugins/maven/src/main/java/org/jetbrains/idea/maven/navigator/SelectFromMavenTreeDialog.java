package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.events.MavenEventsManager;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;

public class SelectFromMavenTreeDialog extends DialogWrapper {
  private Project myProject;
  private SimpleTree myTree;
  private NodeSelector mySelector;

  public SelectFromMavenTreeDialog(Project project,
                                   String title,
                                   final Class<? extends MavenTreeStructure.CustomNode> nodeClass,
                                   NodeSelector selector) {
    super(project, false);
    myProject = project;
    mySelector = selector;
    setTitle(title);

    myTree = new SimpleTree();
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    PopupMavenTreeStructure treeStructure = new PopupMavenTreeStructure(myProject) {
      @Override
      protected Class<? extends CustomNode>[] getVisibleNodesClasses() {
        return new Class[]{nodeClass};
      }
    };
    SimpleTreeBuilder treeBuilder = new SimpleTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), treeStructure, null);
    treeBuilder.initRoot();
    Disposer.register(myProject, treeBuilder);

    SimpleNode nodeToSelect = treeStructure.init();

    treeBuilder.updateFromRoot(true);
    myTree.expandPath(new TreePath(myTree.getModel().getRoot()));

    if (nodeToSelect != null) {
      myTree.setSelectedNode(treeBuilder, nodeToSelect, true);
      // TODO: does not work because of delayed children creation
    }

    init();
  }

  protected SimpleNode getSelectedNode() {
    return myTree.getNodeFor(myTree.getSelectionPath());
  }

  @Nullable
  protected JComponent createCenterPanel() {
    final JScrollPane pane = new JScrollPane(myTree);
    pane.setPreferredSize(new Dimension(320, 400));
    return pane;
  }

  private class PopupMavenTreeStructure extends MavenTreeStructure {
    private PomTreeViewSettings myTreeViewSettings;

    public PopupMavenTreeStructure(final Project project) {
      super(project,
            MavenProjectsManager.getInstance(project),
            MavenEventsManager.getInstance(project));
      myTreeViewSettings = MavenProjectNavigator.getInstance(project).getTreeViewSettings();
    }

    protected PomTreeViewSettings getTreeViewSettings() {
      return myTreeViewSettings;
    }

    protected void updateTreeFrom(SimpleNode node) {
    }

    protected boolean isMinimalView() {
      return true;
    }

    public SimpleNode init() {
      SimpleNode result = null;
      for (MavenProjectModel each : myProjectsManager.getProjects()) {
        PomNode node = new PomNode(each);

        myRoot.addToStructure(node);
        if (result == null && mySelector != null) {
          result = mySelector.findNode(node);
        }
      }
      return result;
    }
  }

  protected interface NodeSelector {
    SimpleNode findNode(MavenTreeStructure.PomNode pomNode);
  }
}