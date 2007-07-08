package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.events.MavenEventsHandler;
import org.jetbrains.idea.maven.repo.MavenRepository;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class SelectMavenGoalDialog extends DialogWrapper {

  private final SimpleTree tree;

  private String pomPath;

  private String goal;

  public SelectMavenGoalDialog(final Project project, final String pomPath, final String goal, final String title) {
    super(project, false);
    setTitle(title);

    tree = new SimpleTree();
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    final PopupPomTreeStructure treeStructure = new PopupPomTreeStructure(project);

    final SimpleTreeBuilder treeBuilder = new SimpleTreeBuilder(tree, (DefaultTreeModel)tree.getModel(), treeStructure, null);
    treeBuilder.initRoot();
    Disposer.register(project, treeBuilder);

    final PomTreeStructure.GoalNode goalNode = treeStructure.init(pomPath, goal);

    treeBuilder.updateFromRoot(true);
    tree.expandPath(new TreePath(tree.getModel().getRoot()));

    if (goalNode != null) {
      tree.setSelectedNode(treeBuilder, goalNode, true);
      // TODO: does not work because of delayed children creation
    }

    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    final JScrollPane pane = new JScrollPane(tree);
    pane.setPreferredSize(new Dimension(320, 400));
    return pane;
  }

  protected void doOKAction() {
    super.doOKAction();

    SimpleNode node = tree.getNodeFor(tree.getSelectionPath());
    if (node instanceof PomTreeStructure.GoalNode) {
      final PomTreeStructure.GoalNode goalNode = (PomTreeStructure.GoalNode)node;
      pomPath = goalNode.getParent(PomTreeStructure.PomNode.class).getFile().getPath();
      goal = goalNode.getGoal();
    }
  }

  public String getSelectedPomPath() {
    return pomPath;
  }

  public String getSelectedGoal() {
    return goal;
  }

  private class PopupPomTreeStructure extends PomTreeStructure {

    private PomTreeViewSettings myTreeViewSettings;

    public PopupPomTreeStructure(final Project project) {
      super(project, project.getComponent(MavenProjectsState.class), project.getComponent(MavenRepository.class),
            project.getComponent(MavenEventsHandler.class));
      myTreeViewSettings = project.getComponent(MavenProjectNavigator.class).getTreeViewSettings();
    }

    protected PomTreeViewSettings getTreeViewSettings() {
      return myTreeViewSettings;
    }

    protected void updateTreeFrom(SimpleNode node) {
    }

    protected boolean isMinimalView() {
      return true;
    }

    @Nullable
    GoalNode init(final String pomPath, final String goalName) {
      GoalNode goalNode = null;
      for (VirtualFile file : myProjectsState.getFiles()) {
        final PomNode pomNode = new PomNode(file);
        root.addToStructure(pomNode);

        if (pomPath != null && pomPath.equals(file.getPath())) {
          goalNode = pomNode.findGoalNode(goalName);
        }
      }
      return goalNode;
    }
  }
}
