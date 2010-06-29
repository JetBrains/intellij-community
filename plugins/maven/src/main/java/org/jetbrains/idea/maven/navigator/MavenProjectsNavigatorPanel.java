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

import com.intellij.execution.Location;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.SimpleTree;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenGoalLocation;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

import java.awt.*;
import java.util.*;
import java.util.List;

public class MavenProjectsNavigatorPanel extends SimpleToolWindowPanel implements DataProvider {
  private final Project myProject;
  private final SimpleTree myTree;

  private Map<String, Integer> standardGoalOrder;

  private final Comparator<String> myGoalOrderComparator = new Comparator<String>() {
    public int compare(String o1, String o2) {
      return getStandardGoalOrder(o1) - getStandardGoalOrder(o2);
    }
  };

  public MavenProjectsNavigatorPanel(Project project, SimpleTree tree) {
    super(true, true);
    myProject = project;
    myTree = tree;

    final ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar actionToolbar = actionManager.createActionToolbar("Maven Navigator Toolbar",
                                                                    (DefaultActionGroup)actionManager
                                                                      .getAction("Maven.NavigatorActionsToolbar"),
                                                                    true);

    actionToolbar.setTargetComponent(tree);
    setToolbar(actionToolbar.getComponent());
    setContent(new JBScrollPane(myTree));

    myTree.addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        final String id = getMenuId(getSelectedNodes(MavenProjectsStructure.MavenSimpleNode.class));
        if (id != null) {
          final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(id);
          if (actionGroup != null) {
            actionManager.createActionPopupMenu("", actionGroup).getComponent().show(comp, x, y);
          }
        }
      }

      @Nullable
      private String getMenuId(Collection<? extends MavenProjectsStructure.MavenSimpleNode> nodes) {
        String id = null;
        for (MavenProjectsStructure.MavenSimpleNode node : nodes) {
          String menuId = node.getMenuId();
          if (menuId == null) {
            return null;
          }
          if (id == null) {
            id = menuId;
          }
          else if (!id.equals(menuId)) {
            return null;
          }
        }
        return id;
      }
    });
  }

  @Nullable
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) return "reference.toolWindows.mavenProjects";

    if (PlatformDataKeys.PROJECT.is(dataId)) return myProject;

    if (PlatformDataKeys.VIRTUAL_FILE.is(dataId)) return extractVirtualFile();
    if (PlatformDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) return extractVirtualFiles();

    if (Location.DATA_KEY.is(dataId)) return extractLocation();
    if (PlatformDataKeys.NAVIGATABLE_ARRAY.is(dataId)) return extractNavigatables();

    if (MavenDataKeys.MAVEN_GOALS.is(dataId)) return extractGoals();
    if (MavenDataKeys.MAVEN_PROFILES.is(dataId)) return extractProfiles();

    if (MavenDataKeys.MAVEN_DEPENDENCIES.is(dataId)) {
      return extractDependencies();
    }
    if (MavenDataKeys.MAVEN_PROJECTS_TREE.is(dataId)) {
      return myTree;
    }

    return super.getData(dataId);
  }

  private VirtualFile extractVirtualFile() {
    for (MavenProjectsStructure.MavenSimpleNode each : getSelectedNodes(MavenProjectsStructure.MavenSimpleNode.class)) {
      VirtualFile file = each.getVirtualFile();
      if (file != null && file.isValid()) return file;
    }

    final MavenProjectsStructure.ProjectNode projectNode = getContextProjectNode();
    if (projectNode == null) return null;
    VirtualFile file = projectNode.getVirtualFile();
    if (file == null || !file.isValid()) return null;
    return file;
  }

  private Object extractVirtualFiles() {
    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    for (MavenProjectsStructure.MavenSimpleNode each : getSelectedNodes(MavenProjectsStructure.MavenSimpleNode.class)) {
      VirtualFile file = each.getVirtualFile();
      if (file != null && file.isValid()) files.add(file);
    }
    return files.isEmpty() ? null : VfsUtil.toVirtualFileArray(files);
  }

  private Object extractNavigatables() {
    final List<Navigatable> navigatables = new ArrayList<Navigatable>();
    for (MavenProjectsStructure.MavenSimpleNode each : getSelectedNodes(MavenProjectsStructure.MavenSimpleNode.class)) {
      Navigatable navigatable = each.getNavigatable();
      if (navigatable != null) navigatables.add(navigatable);
    }
    return navigatables.isEmpty() ? null : navigatables.toArray(new Navigatable[navigatables.size()]);
  }

  private Object extractLocation() {
    VirtualFile file = extractVirtualFile();
    List<String> goals = extractGoals();
    if (file == null || goals == null) return null;

    return new MavenGoalLocation(myProject, file, extractGoals());
  }

  private List<String> extractGoals() {
    final MavenProjectsStructure.ProjectNode projectNode = getSelectedProjectNode();
    if (projectNode != null) {
      MavenProject project = projectNode.getMavenProject();
      String goal = project.getDefaultGoal();
      if (!StringUtil.isEmptyOrSpaces(goal)) {
        return Collections.singletonList(goal);
      }
    }
    else {
      final List<MavenProjectsStructure.GoalNode> nodes = getSelectedNodes(MavenProjectsStructure.GoalNode.class);
      if (MavenProjectsStructure.getCommonProjectNode(nodes) == null) {
        return null;
      }
      final List<String> goals = new ArrayList<String>();
      for (MavenProjectsStructure.GoalNode node : nodes) {
        goals.add(node.getGoal());
      }
      Collections.sort(goals, myGoalOrderComparator);
      return goals;
    }
    return null;
  }

  private Object extractProfiles() {
    final List<MavenProjectsStructure.ProfileNode> nodes = getSelectedNodes(MavenProjectsStructure.ProfileNode.class);
    final List<String> profiles = new ArrayList<String>();
    for (MavenProjectsStructure.ProfileNode node : nodes) {
      profiles.add(node.getProfileName());
    }
    return profiles;
  }

  private Set<MavenArtifact> extractDependencies() {
    Set<MavenArtifact> result = new THashSet<MavenArtifact>();

    List<MavenProjectsStructure.ProjectNode> projectNodes = getSelectedProjectNodes();
    if (!projectNodes.isEmpty()) {
      for (MavenProjectsStructure.ProjectNode each : projectNodes) {
        result.addAll(each.getMavenProject().getDependencies());
      }
      return result;
    }

    List<MavenProjectsStructure.BaseDependenciesNode> nodes = getSelectedNodes(MavenProjectsStructure.BaseDependenciesNode.class);
    for (MavenProjectsStructure.BaseDependenciesNode each : nodes) {
      if (each instanceof MavenProjectsStructure.DependenciesNode) {
        result.addAll(each.getMavenProject().getDependencies());
      }
      else {
        result.add(((MavenProjectsStructure.DependencyNode)each).getArtifact());
      }
    }
    return result;
  }

  private <T extends MavenProjectsStructure.MavenSimpleNode> List<T> getSelectedNodes(Class<T> aClass) {
    return MavenProjectsStructure.getSelectedNodes(myTree, aClass);
  }

  private List<MavenProjectsStructure.ProjectNode> getSelectedProjectNodes() {
    return getSelectedNodes(MavenProjectsStructure.ProjectNode.class);
  }

  @Nullable
  private MavenProjectsStructure.ProjectNode getSelectedProjectNode() {
    final List<MavenProjectsStructure.ProjectNode> projectNodes = getSelectedProjectNodes();
    return projectNodes.size() == 1 ? projectNodes.get(0) : null;
  }

  @Nullable
  private MavenProjectsStructure.ProjectNode getContextProjectNode() {
    MavenProjectsStructure.ProjectNode projectNode = getSelectedProjectNode();
    if (projectNode != null) return projectNode;
    return MavenProjectsStructure.getCommonProjectNode(getSelectedNodes(MavenProjectsStructure.MavenSimpleNode.class));
  }

  private int getStandardGoalOrder(String goal) {
    if (standardGoalOrder == null) {
      standardGoalOrder = new THashMap<String, Integer>();
      int i = 0;
      for (String aGoal : MavenConstants.PHASES) {
        standardGoalOrder.put(aGoal, i++);
      }
    }
    Integer order = standardGoalOrder.get(goal);
    return order != null ? order.intValue() : standardGoalOrder.size();
  }
}
