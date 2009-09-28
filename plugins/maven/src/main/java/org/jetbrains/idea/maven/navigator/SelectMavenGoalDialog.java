package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.treeStructure.SimpleNode;

public class SelectMavenGoalDialog extends SelectFromMavenProjectsDialog {
  private String myProjectPath;
  private String myGoal;

  public SelectMavenGoalDialog(Project project, final String projectPath, final String goal, String title) {
    super(project, title, MavenProjectsStructure.GoalNode.class, new NodeSelector() {
      public boolean shouldSelect(SimpleNode node) {
        if (node instanceof MavenProjectsStructure.GoalNode) {
          MavenProjectsStructure.GoalNode goalNode = (MavenProjectsStructure.GoalNode)node;
          return FileUtil.pathsEqual(goalNode.getProjectPath(), projectPath)
                 && goalNode.getGoal().equals(goal);
        }
        return false;
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
