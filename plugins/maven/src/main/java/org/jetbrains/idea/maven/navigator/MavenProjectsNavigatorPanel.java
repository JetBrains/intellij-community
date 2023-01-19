// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.ui.mac.touchbar.Touchbar;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.containers.ContainerUtil;
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

public final class MavenProjectsNavigatorPanel extends SimpleToolWindowPanel implements DataProvider {
  private final Project myProject;
  private final SimpleTree myTree;

  private final Comparator<String> myGoalOrderComparator = new Comparator<>() {

    private Map<String, Integer> standardGoalOrder;

    @Override
    public int compare(String o1, String o2) {
      return getStandardGoalOrder(o1) - getStandardGoalOrder(o2);
    }

    private int getStandardGoalOrder(String goal) {
      if (standardGoalOrder == null) {
        standardGoalOrder = new HashMap<>();
        int i = 0;
        for (String aGoal : MavenConstants.PHASES) {
          standardGoalOrder.put(aGoal, i++);
        }
      }
      Integer order = standardGoalOrder.get(goal);
      return order != null ? order.intValue() : standardGoalOrder.size();
    }
  };

  SimpleTree getTree() {
    return myTree;
  }

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
        final String id = getMenuId(MavenProjectsStructure.getSelectedNodes(myTree, MavenProjectsStructure.MavenSimpleNode.class));
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

    Touchbar.setActions(this, "Maven.Reimport");
  }

  @Override
  @Nullable
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
      @NotNull List<MavenProjectsStructure.MavenSimpleNode> selectedNodes =
        MavenProjectsStructure.getSelectedNodes(myTree, MavenProjectsStructure.MavenSimpleNode.class);
      return (DataProvider)slowId -> getSlowData(slowId, selectedNodes);
    }
    if (PlatformCoreDataKeys.HELP_ID.is(dataId)) return "reference.toolWindows.mavenProjects";
    if (CommonDataKeys.PROJECT.is(dataId)) return myProject;

    if (MavenDataKeys.MAVEN_PROJECTS_TREE.is(dataId)) {
      return myTree;
    }
    return super.getData(dataId);
  }

  private @Nullable Object getSlowData(@NotNull String dataId,
                                       @NotNull List<MavenProjectsStructure.MavenSimpleNode> selectedNodes) {
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) return extractVirtualFile(selectedNodes);
    if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) return extractVirtualFiles(selectedNodes);

    if (Location.DATA_KEY.is(dataId)) return extractLocation(selectedNodes);
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) return extractNavigatables(selectedNodes);

    if (MavenDataKeys.MAVEN_GOALS.is(dataId)) return extractGoals(true, selectedNodes);
    if (MavenDataKeys.RUN_CONFIGURATION.is(dataId)) return extractRunSettings(selectedNodes);
    if (MavenDataKeys.MAVEN_PROFILES.is(dataId)) return extractProfiles(selectedNodes);

    if (MavenDataKeys.MAVEN_DEPENDENCIES.is(dataId)) {
      return extractDependencies(selectedNodes);
    }

    return null;
  }

  private VirtualFile extractVirtualFile(@NotNull List<MavenProjectsStructure.MavenSimpleNode> selectedNodes) {

    for (MavenProjectsStructure.MavenSimpleNode each : selectedNodes) {
      VirtualFile file = each.getVirtualFile();
      if (file != null && file.isValid()) return file;
    }

    final MavenProjectsStructure.ProjectNode projectNode = getContextProjectNode(selectedNodes);
    if (projectNode == null) return null;
    VirtualFile file = projectNode.getVirtualFile();
    if (file == null || !file.isValid()) return null;
    return file;
  }

  private Object extractVirtualFiles(@NotNull List<MavenProjectsStructure.MavenSimpleNode> selectedNodes) {
    final List<VirtualFile> files = new ArrayList<>();
    for (MavenProjectsStructure.MavenSimpleNode each : selectedNodes) {
      VirtualFile file = each.getVirtualFile();
      if (file != null && file.isValid()) files.add(file);
    }
    return files.isEmpty() ? null : VfsUtil.toVirtualFileArray(files);
  }

  private Object extractNavigatables(@NotNull List<MavenProjectsStructure.MavenSimpleNode> selectedNodes) {
    final List<Navigatable> navigatables = new ArrayList<>();
    for (MavenProjectsStructure.MavenSimpleNode each : selectedNodes) {
      Navigatable navigatable = each.getNavigatable();
      if (navigatable != null) navigatables.add(navigatable);
    }
    return navigatables.isEmpty() ? null : navigatables.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY);
  }

  private Object extractLocation(@NotNull List<MavenProjectsStructure.MavenSimpleNode> selectedNodes) {
    VirtualFile file = extractVirtualFile(selectedNodes);
    if (file == null) return null;

    List<String> goals = extractGoals(false, selectedNodes);
    if (goals == null) return null;

    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    return psiFile == null ? null : new MavenGoalLocation(myProject, psiFile, goals);
  }

  @Nullable
  private RunnerAndConfigurationSettings extractRunSettings(@NotNull List<MavenProjectsStructure.MavenSimpleNode> selectedNodes) {
    @Nullable MavenProjectsStructure.MavenSimpleNode node = selectedNodes.isEmpty() ? null : selectedNodes.get(0);
    if (!(node instanceof MavenProjectsStructure.RunConfigurationNode)) return null;

    return ((MavenProjectsStructure.RunConfigurationNode)node).getSettings();
  }

  private List<String> extractGoals(boolean qualifiedGoals,
                                    @NotNull List<MavenProjectsStructure.MavenSimpleNode> selectedNodes) {

    List<MavenProjectsStructure.ProjectNode> projectNodes = filterNodesByClass(selectedNodes, MavenProjectsStructure.ProjectNode.class);
    final MavenProjectsStructure.ProjectNode projectNode = projectNodes.size() == 1 ? projectNodes.get(0) : null;
    if (projectNode != null) {
      MavenProject project = projectNode.getMavenProject();
      String goal = project.getDefaultGoal();
      if (!StringUtil.isEmptyOrSpaces(goal)) {
        // Maven uses StringTokenizer to split defaultGoal. See DefaultLifecycleTaskSegmentCalculator#calculateTaskSegments()
        return ContainerUtil.newArrayList(StringUtil.tokenize(new StringTokenizer(goal)));
      }
    }
    else {
      List<MavenProjectsStructure.GoalNode> goalNodes = filterNodesByClass(selectedNodes, MavenProjectsStructure.GoalNode.class);
      if (MavenProjectsStructure.getCommonProjectNode(goalNodes) == null) {
        return null;
      }

      final List<String> goals = new ArrayList<>();
      for (MavenProjectsStructure.GoalNode node : goalNodes) {
        goals.add(qualifiedGoals ? node.getGoal() : node.getName());
      }
      goals.sort(myGoalOrderComparator);
      return goals;
    }
    return null;
  }

  private Object extractProfiles(@NotNull List<MavenProjectsStructure.MavenSimpleNode> selectedNodes) {
    List<MavenProjectsStructure.ProfileNode> profileNodes = filterNodesByClass(selectedNodes, MavenProjectsStructure.ProfileNode.class);
    final Map<String, MavenProfileKind> profiles = new HashMap<>();
    for (MavenProjectsStructure.ProfileNode node : profileNodes) {
      profiles.put(node.getProfileName(), node.getState());
    }
    return profiles;
  }

  private Set<MavenArtifact> extractDependencies(@NotNull List<MavenProjectsStructure.MavenSimpleNode> selectedNodes) {
    Set<MavenArtifact> result = new HashSet<>();
    List<MavenProjectsStructure.ProjectNode> projectNodes = filterNodesByClass(selectedNodes, MavenProjectsStructure.ProjectNode.class);


    if (!projectNodes.isEmpty()) {
      for (MavenProjectsStructure.ProjectNode each : projectNodes) {
        result.addAll(each.getMavenProject().getDependencies());
      }
      return result;
    }


    List<MavenProjectsStructure.BaseDependenciesNode> baseDependenciesNodes =
      filterNodesByClass(selectedNodes, MavenProjectsStructure.BaseDependenciesNode.class);

    for (MavenProjectsStructure.BaseDependenciesNode each : baseDependenciesNodes) {
      if (each instanceof MavenProjectsStructure.DependenciesNode) {
        result.addAll(each.getMavenProject().getDependencies());
      }
      else {
        result.add(((MavenProjectsStructure.DependencyNode)each).getArtifact());
      }
    }
    return result;
  }

  private <T extends MavenProjectsStructure.MavenSimpleNode> List<T> filterNodesByClass(@NotNull List<MavenProjectsStructure.MavenSimpleNode> nodes,
                                                                                        Class<T> aClass) {
    List<T> filtered = ContainerUtil.filterIsInstance(nodes, aClass);
    return filtered.size() == nodes.size() ? filtered : Collections.emptyList();
  }


  private MavenProjectsStructure.@Nullable ProjectNode getContextProjectNode(@NotNull List<MavenProjectsStructure.MavenSimpleNode> selectedNodes) {
    List<MavenProjectsStructure.ProjectNode> projectNodes = filterNodesByClass(selectedNodes, MavenProjectsStructure.ProjectNode.class);
    MavenProjectsStructure.ProjectNode projectNode = projectNodes.size() == 1 ? projectNodes.get(0) : null;
    if (projectNode != null) return projectNode;
    return MavenProjectsStructure.getCommonProjectNode(selectedNodes);
  }

  private static final class MyTransferHandler extends TransferHandler {

    private final Project myProject;

    private MyTransferHandler(Project project) {
      myProject = project;
    }

    @Override
    public boolean importData(final TransferSupport support) {

      //todo new maven importing
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