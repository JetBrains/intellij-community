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
import org.jetbrains.idea.maven.project.MavenProject;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class SelectMavenProjectDialog extends SelectFromMavenProjectsDialog {
  private MavenProject myResult;

  public SelectMavenProjectDialog(Project project, final MavenProject current) {
    super(project, "Select Maven Project", MavenProjectsStructure.ProjectNode.class, new NodeSelector() {
      public boolean shouldSelect(SimpleNode node) {
        if (node instanceof MavenProjectsStructure.ProjectNode) {
          return ((MavenProjectsStructure.ProjectNode)node).getMavenProject() == current;
        }
        return false;
      }
    });

    init();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    Action selectNoneAction = new AbstractAction("&None") {
      public void actionPerformed(ActionEvent e) {
        doOKAction();
        myResult = null;
      }
    };
    return new Action[]{selectNoneAction, getOKAction(), getCancelAction()};
  }

  @Override
  protected void doOKAction() {
    SimpleNode node = getSelectedNode();
    if (node instanceof NullNode) node = null;

    myResult = node instanceof MavenProjectsStructure.ProjectNode ? ((MavenProjectsStructure.ProjectNode)node).getMavenProject() : null;
    super.doOKAction();
  }

  public MavenProject getResult() {
    return myResult;
  }
}
