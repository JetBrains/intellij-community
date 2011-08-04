/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.treeStructure.SimpleNode;

public class SelectMavenGoalDialog extends SelectFromMavenProjectsDialog {
  private String myProjectPath;
  private String myGoal;

  public SelectMavenGoalDialog(Project project, final String projectPath, final String goal, String title) {
    super(project, title, MavenProjectsStructure.GoalNode.class, new NodeSelector() {
      public boolean shouldSelect(SimpleNode node) {
        if (node instanceof MavenProjectsStructure.GoalNode) {
          MavenProjectsStructure.GoalNode eachGoalNode = (MavenProjectsStructure.GoalNode)node;
          String goalNodeProjectPath = eachGoalNode.getProjectPath();
          return goalNodeProjectPath != null && projectPath != null
                 && FileUtil.pathsEqual(goalNodeProjectPath, projectPath)
                 && Comparing.equal(eachGoalNode.getGoal(), goal);
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
