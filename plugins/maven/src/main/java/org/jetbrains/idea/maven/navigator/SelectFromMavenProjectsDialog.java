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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleNodeVisitor;
import com.intellij.ui.treeStructure.SimpleTree;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;

import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;

public class SelectFromMavenProjectsDialog extends DialogWrapper {
  private final Project myProject;
  private final SimpleTree myTree;
  private final NodeSelector mySelector;

  public SelectFromMavenProjectsDialog(Project project,
                                       String title,
                                       final Class<? extends MavenProjectsStructure.MavenSimpleNode> nodeClass,
                                       NodeSelector selector) {
    super(project, false);
    myProject = project;
    mySelector = selector;
    setTitle(title);

    myTree = new SimpleTree();
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    MavenProjectsStructure treeStructure = new MavenProjectsStructure(myProject,
                                                                      MavenProjectsManager.getInstance(myProject),
                                                                      MavenTasksManager.getInstance(myProject),
                                                                      MavenShortcutsManager.getInstance(myProject),
                                                                      MavenProjectsNavigator.getInstance(myProject),
                                                                      myTree) {
      @Override
      protected Class<? extends MavenSimpleNode>[] getVisibleNodesClasses() {
        return new Class[]{nodeClass};
      }

      @Override
      protected boolean showDescriptions() {
        return false;
      }

      @Override
      protected boolean showOnlyBasicPhases() {
        return false;
      }
    };
    treeStructure.update();

    final SimpleNode[] selection = new SimpleNode[]{null};
    treeStructure.accept(new SimpleNodeVisitor() {
      public boolean accept(SimpleNode each) {
        if (!mySelector.shouldSelect(each)) return false;
        selection[0] = each;
        return true;
      }
    });
    if (selection[0] != null) {
      treeStructure.select(selection[0]);
    }

    init();
  }

  protected SimpleNode getSelectedNode() {
    return myTree.getNodeFor(myTree.getSelectionPath());
  }

  @Nullable
  protected JComponent createCenterPanel() {
    final JBScrollPane pane = new JBScrollPane(myTree);
    pane.setPreferredSize(new Dimension(320, 400));
    return pane;
  }

  protected interface NodeSelector {
    boolean shouldSelect(SimpleNode node);
  }
}
