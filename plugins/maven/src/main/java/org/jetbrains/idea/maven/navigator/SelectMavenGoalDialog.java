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
import com.intellij.ui.treeStructure.NullNode;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SelectMavenGoalDialog extends SelectFromMavenProjectsDialog {
  private MavenProjectsStructure.GoalNode myResult;

  public SelectMavenGoalDialog(Project project) {
    super(project, "Choose Maven Goal", MavenProjectsStructure.GoalNode.class);
    init();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Override
  protected void doOKAction() {
    SimpleNode node = getSelectedNode();
    if (node instanceof NullNode) node = null;

    myResult = node instanceof MavenProjectsStructure.GoalNode ? ((MavenProjectsStructure.GoalNode)node) : null;
    super.doOKAction();
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();
    myResult = null;
  }

  public MavenProjectsStructure.GoalNode getResult() {
    return myResult;
  }
}
