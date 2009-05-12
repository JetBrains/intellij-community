package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.SimpleNode;

public class SelectMavenGoalDialog extends SelectFromMavenProjectsDialog {
  private String myProjectPath;
  private String myGoal;

  public SelectMavenGoalDialog(Project project, final String projectPath, final String goal, String title) {
    super(project, title, MavenProjectsStructure.GoalNode.class, new NodeSelector() {
      public SimpleNode findNode(MavenProjectsStructure.ProjectNode pomNode) {
        if (pomNode.getMavenProject().getPath().equals(projectPath)) {
          return pomNode.findGoalNode(goal);
        }
        return null;
      }
    });

    myProjectPath = projectPath;
    myGoal = goal;

    init();
  }

  protected void doOKAction() {
    super.doOKAction();

    SimpleNode node = getSelectedNode();
    if (node instanceof MavenProjectsStructure.GoalNode) {
      MavenProjectsStructure.GoalNode goalNode = (MavenProjectsStructure.GoalNode)node;
      myProjectPath = goalNode.getProjectPath();
      myGoal = goalNode.getGoal();
    }
  }

  public String getSelectedProjectPath() {
    return myProjectPath;
  }

  public String getSelectedGoal() {
    return myGoal;
  }
}
