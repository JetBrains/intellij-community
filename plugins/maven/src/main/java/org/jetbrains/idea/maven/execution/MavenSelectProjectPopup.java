/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution;

import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenProjectNamer;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class MavenSelectProjectPopup {

  public static void attachToWorkingDirectoryField(@NotNull final MavenProjectsManager projectsManager,
                                                   final JTextField workingDirectoryField,
                                                   final JButton showModulesButton,
                                                   @Nullable final JComponent focusAfterSelection) {
    attachToButton(projectsManager, showModulesButton, project -> {
      workingDirectoryField.setText(project.getDirectory());

      if (focusAfterSelection != null) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (workingDirectoryField.hasFocus()) {
            focusAfterSelection.requestFocus();
          }
        });
      }
    });

    workingDirectoryField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
          if (!e.isConsumed()) { // May be consumed by path completion.
            e.consume();
            showModulesButton.doClick();
          }
        }
      }
    });
  }

  public static void attachToButton(@NotNull final MavenProjectsManager projectsManager,
                                    @NotNull final JButton button,
                                    @NotNull final Consumer<MavenProject> callback) {
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        List<MavenProject> projectList = projectsManager.getProjects();
        if (projectList.isEmpty()) {
          JBPopupFactory.getInstance().createMessage("Maven projects not found").showUnderneathOf(button);
          return;
        }

        DefaultMutableTreeNode root = buildTree(projectList);

        final Map<MavenProject, String> projectsNameMap = MavenProjectNamer.generateNameMap(projectList);

        final Tree projectTree = new Tree(root);
        projectTree.setRootVisible(false);
        projectTree.setCellRenderer(new NodeRenderer() {
          @Override
          public void customizeCellRenderer(JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            if (value instanceof DefaultMutableTreeNode) {
              MavenProject mavenProject = (MavenProject)((DefaultMutableTreeNode)value).getUserObject();
              value = projectsNameMap.get(mavenProject);
              setIcon(MavenIcons.MavenProject);
            }

            super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
          }
        });

        new TreeSpeedSearch(projectTree, new Convertor<TreePath, String>() {
          @Override
          public String convert(TreePath o) {
            Object lastPathComponent = o.getLastPathComponent();
            if (!(lastPathComponent instanceof DefaultMutableTreeNode)) return null;

            Object userObject = ((DefaultMutableTreeNode)lastPathComponent).getUserObject();

            //noinspection SuspiciousMethodCalls
            return projectsNameMap.get(userObject);
          }
        });

        final Ref<JBPopup> popupRef = new Ref<>();

        final Runnable clickCallBack = () -> {
          TreePath path = projectTree.getSelectionPath();
          if (path == null) return;

          Object lastPathComponent = path.getLastPathComponent();
          if (!(lastPathComponent instanceof DefaultMutableTreeNode)) return;

          Object object = ((DefaultMutableTreeNode)lastPathComponent).getUserObject();
          if (object == null) return; // may be it's the root

          callback.consume((MavenProject)object);

          popupRef.get().closeOk(null);
        };

        projectTree.addKeyListener(new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
              clickCallBack.run();
              e.consume();
            }
          }
        });

        JBPopup popup = new PopupChooserBuilder(projectTree)
          .setTitle("Select maven project")
          .setResizable(true)
          .setItemChoosenCallback(clickCallBack).setAutoselectOnMouseMove(true)
          .setCloseOnEnter(false)
          .createPopup();

        popupRef.set(popup);

        popup.showUnderneathOf(button);
      }

      private DefaultMutableTreeNode buildTree(List<MavenProject> projectList) {
        MavenProject[] projects = projectList.toArray(new MavenProject[projectList.size()]);
        Arrays.sort(projects, new MavenProjectNamer.MavenProjectComparator());

        Map<MavenProject, DefaultMutableTreeNode> projectsToNode = new HashMap<>();
        for (MavenProject mavenProject : projects) {
          projectsToNode.put(mavenProject, new DefaultMutableTreeNode(mavenProject));
        }

        DefaultMutableTreeNode root = new DefaultMutableTreeNode();

        for (MavenProject mavenProject : projects) {
          DefaultMutableTreeNode parent;

          MavenProject aggregator = projectsManager.findAggregator(mavenProject);
          if (aggregator != null) {
            parent = projectsToNode.get(aggregator);
          }
          else {
            parent = root;
          }

          parent.add(projectsToNode.get(mavenProject));
        }

        return root;
      }
    });
  }
}
