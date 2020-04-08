// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.navigator;

import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.mac.TouchbarDataKeys;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenGoalLocation;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenProfileKind;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;

import static org.jetbrains.idea.maven.navigator.MavenProjectsNavigator.TOOL_WINDOW_PLACE_ID;

public class MavenProjectsNavigatorPanel extends SimpleToolWindowPanel implements DataProvider {
  private final Project myProject;
  private final SimpleTree myTree;

  private final Comparator<String> myGoalOrderComparator = new Comparator<String>() {

    private Map<String, Integer> standardGoalOrder;

    @Override
    public int compare(String o1, String o2) {
      return getStandardGoalOrder(o1) - getStandardGoalOrder(o2);
    }

    private int getStandardGoalOrder(String goal) {
      if (standardGoalOrder == null) {
        standardGoalOrder = new THashMap<>();
        int i = 0;
        for (String aGoal : MavenConstants.PHASES) {
          standardGoalOrder.put(aGoal, i++);
        }
      }
      Integer order = standardGoalOrder.get(goal);
      return order != null ? order.intValue() : standardGoalOrder.size();
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
    setContent(ScrollPaneFactory.createScrollPane(myTree));

    setTransferHandler(new MyTransferHandler(project));

    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component comp, final int x, final int y) {
        final String id = getMenuId(getSelectedNodes(MavenProjectsStructure.MavenSimpleNode.class));
        if (id != null) {
          final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(id);
          if (actionGroup != null) {
            actionManager.createActionPopupMenu(TOOL_WINDOW_PLACE_ID, actionGroup).getComponent().show(comp, x, y);
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

  @Override
  @Nullable
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) return "reference.toolWindows.mavenProjects";

    if (CommonDataKeys.PROJECT.is(dataId)) return myProject;

    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) return extractVirtualFile();
    if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) return extractVirtualFiles();

    if (Location.DATA_KEY.is(dataId)) return extractLocation();
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) return extractNavigatables();

    if (MavenDataKeys.MAVEN_GOALS.is(dataId)) return extractGoals(true);
    if (MavenDataKeys.RUN_CONFIGURATION.is(dataId)) return extractRunSettings();
    if (MavenDataKeys.MAVEN_PROFILES.is(dataId)) return extractProfiles();

    if (MavenDataKeys.MAVEN_DEPENDENCIES.is(dataId)) {
      return extractDependencies();
    }
    if (MavenDataKeys.MAVEN_PROJECTS_TREE.is(dataId)) {
      return myTree;
    }
    if (TouchbarDataKeys.ACTIONS_KEY.is(dataId)) {
      return new DefaultActionGroup(ActionManager.getInstance().getAction("Maven.Reimport"));
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
    final List<VirtualFile> files = new ArrayList<>();
    for (MavenProjectsStructure.MavenSimpleNode each : getSelectedNodes(MavenProjectsStructure.MavenSimpleNode.class)) {
      VirtualFile file = each.getVirtualFile();
      if (file != null && file.isValid()) files.add(file);
    }
    return files.isEmpty() ? null : VfsUtil.toVirtualFileArray(files);
  }

  private Object extractNavigatables() {
    final List<Navigatable> navigatables = new ArrayList<>();
    for (MavenProjectsStructure.MavenSimpleNode each : getSelectedNodes(MavenProjectsStructure.MavenSimpleNode.class)) {
      Navigatable navigatable = each.getNavigatable();
      if (navigatable != null) navigatables.add(navigatable);
    }
    return navigatables.isEmpty() ? null : navigatables.toArray(new Navigatable[0]);
  }

  private Object extractLocation() {
    VirtualFile file = extractVirtualFile();
    if (file == null) return null;

    List<String> goals = extractGoals(false);
    if (goals == null) return null;

    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    return psiFile == null ? null : new MavenGoalLocation(myProject, psiFile, goals);
  }

  @Nullable
  private RunnerAndConfigurationSettings extractRunSettings() {
    SimpleNode node = myTree.getSelectedNode();
    if (!(node instanceof MavenProjectsStructure.RunConfigurationNode)) return null;

    return ((MavenProjectsStructure.RunConfigurationNode)node).getSettings();
  }

  private List<String> extractGoals(boolean qualifiedGoals) {
    final MavenProjectsStructure.ProjectNode projectNode = getSelectedProjectNode();
    if (projectNode != null) {
      MavenProject project = projectNode.getMavenProject();
      String goal = project.getDefaultGoal();
      if (!StringUtil.isEmptyOrSpaces(goal)) {
        // Maven uses StringTokenizer to split defaultGoal. See DefaultLifecycleTaskSegmentCalculator#calculateTaskSegments()
        return ContainerUtil.newArrayList(StringUtil.tokenize(new StringTokenizer(goal)));
      }
    }
    else {
      final List<MavenProjectsStructure.GoalNode> nodes = getSelectedNodes(MavenProjectsStructure.GoalNode.class);
      if (MavenProjectsStructure.getCommonProjectNode(nodes) == null) {
        return null;
      }
      final List<String> goals = new ArrayList<>();
      for (MavenProjectsStructure.GoalNode node : nodes) {
        goals.add(qualifiedGoals ? node.getGoal() : node.getName());
      }
      goals.sort(myGoalOrderComparator);
      return goals;
    }
    return null;
  }

  private Object extractProfiles() {
    final List<MavenProjectsStructure.ProfileNode> nodes = getSelectedNodes(MavenProjectsStructure.ProfileNode.class);
    final Map<String, MavenProfileKind> profiles = new THashMap<>();
    for (MavenProjectsStructure.ProfileNode node : nodes) {
      profiles.put(node.getProfileName(), node.getState());
    }
    return profiles;
  }

  private Set<MavenArtifact> extractDependencies() {
    Set<MavenArtifact> result = new THashSet<>();

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

  private static class MyTransferHandler extends TransferHandler {

    private final Project myProject;

    private MyTransferHandler(Project project) {
      myProject = project;
    }

    @Override
    public boolean importData(final TransferSupport support) {
      if (canImport(support)) {
        List<VirtualFile> pomFiles = new ArrayList<>();

        final List<File> fileList = FileCopyPasteUtil.getFileList(support.getTransferable());
        if (fileList == null) return false;

        MavenProjectsManager manager = MavenProjectsManager.getInstance(myProject);

        for (File file : fileList) {
          VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, true);
          if (file.isFile()
              && virtualFile != null
              && MavenActionUtil.isMavenProjectFile(virtualFile)
              && !manager.isManagedFile(virtualFile)) {
            pomFiles.add(virtualFile);
          }
        }

        if (pomFiles.isEmpty()) {
          return false;
        }

        manager.addManagedFilesOrUnignore(pomFiles);

        return true;
      }
      return false;
    }

    @Override
    public boolean canImport(final TransferSupport support) {
      return FileCopyPasteUtil.isFileListFlavorAvailable(support.getDataFlavors());
    }
  }
}
