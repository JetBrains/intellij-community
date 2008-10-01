package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.SimpleNode;

public class SelectMavenGoalDialog extends SelectFromMavenTreeDialog {
  private String myPomPath;
  private String myGoal;

  public SelectMavenGoalDialog(Project project, final String pomPath, final String goal, String title) {
    super(project, title, MavenTreeStructure.GoalNode.class, new NodeSelector() {
      public SimpleNode findNode(MavenTreeStructure.PomNode pomNode) {
        if (pomNode.getProjectModel().getPath().equals(pomPath)) {
          return pomNode.findGoalNode(goal);
        }
        return null;
      }
    });

    myPomPath = pomPath;
    myGoal = goal;

    init();
  }

  protected void doOKAction() {
    super.doOKAction();

    SimpleNode node = getSelectedNode();
    if (node instanceof MavenTreeStructure.GoalNode) {
      MavenTreeStructure.GoalNode goalNode = (MavenTreeStructure.GoalNode)node;
      myPomPath = goalNode.getParent(MavenTreeStructure.PomNode.class).getProjectModel().getPath();
      myGoal = goalNode.getGoal();
    }
  }

  public String getSelectedPomPath() {
    return myPomPath;
  }

  public String getSelectedGoal() {
    return myGoal;
  }
}
