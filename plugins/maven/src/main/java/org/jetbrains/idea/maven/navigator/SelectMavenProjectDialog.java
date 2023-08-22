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

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.NullNode;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.navigator.structure.MavenProjectNode;
import org.jetbrains.idea.maven.navigator.structure.MavenProjectsStructure;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class SelectMavenProjectDialog extends SelectFromMavenProjectsDialog {
  private MavenProject myResult;

  public SelectMavenProjectDialog(Project project, final MavenProject current) {
    super(project, MavenProjectBundle.message("dialog.title.select.maven.project"), MavenProjectsStructure.MavenStructureDisplayMode.SHOW_PROJECTS, new NodeSelector() {
      @Override
      public boolean shouldSelect(SimpleNode node) {
        if (node instanceof MavenProjectNode) {
          return ((MavenProjectNode)node).getMavenProject() == current;
        }
        return false;
      }
    });

    init();
  }

  @Override
  protected Action @NotNull [] createActions() {
    Action selectNoneAction = new AbstractAction(CommonBundle.message("action.text.none")) {
      @Override
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

    myResult = node instanceof MavenProjectNode ? ((MavenProjectNode)node).getMavenProject() : null;
    super.doOKAction();
  }

  public MavenProject getResult() {
    return myResult;
  }
}
