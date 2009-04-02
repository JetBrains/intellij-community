package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.SimpleNode;

public class SelectMavenGoalDialog extends SelectFromMavenProjectsDialog {
  private String myPomPath;
  private String myGoal;

  public SelectMavenGoalDialog(Project project, final String pomPath, final String goal, String title) {
    super(project, title, MavenProjectsStructure.GoalNode.class, new NodeSelector() {
      public SimpleNode findNode(MavenProjectsStructure.PomNode pomNode) {
        if (pomNode.getMavenProject().getPath().equals(pomPath)) {
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
    if (node instanceof MavenProjectsStructure.GoalNode) {
      MavenProjectsStructure.GoalNode goalNode = (MavenProjectsStructure.GoalNode)node;
      myPomPath = goalNode.getParent(MavenProjectsStructure.PomNode.class).getMavenProject().getPath();
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
