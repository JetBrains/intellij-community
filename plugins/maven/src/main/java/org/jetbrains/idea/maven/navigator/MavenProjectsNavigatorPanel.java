package org.jetbrains.idea.maven.navigator;

import com.intellij.execution.Location;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.runner.MavenGoalLocation;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class MavenProjectsNavigatorPanel extends SimpleToolWindowPanel implements DataProvider {
  private final Project myProject;
  private final SimpleTree myTree;
  private final JPanel myStatusPanel;

  private Map<String, Integer> standardGoalOrder;

  private final Comparator<String> myGoalOrderComparator = new Comparator<String>() {
    public int compare(String o1, String o2) {
      return getStandardGoalOrder(o1) - getStandardGoalOrder(o2);
    }
  };

  public MavenProjectsNavigatorPanel(Project project, SimpleTree tree, JPanel statusPanel) {
    super(true, true);
    myProject = project;
    myTree = tree;
    myStatusPanel = statusPanel;

    JComponent toolbar = ActionManager.getInstance().createActionToolbar("New Maven Toolbar", (ActionGroup)ActionManager.getInstance()
      .getAction("Maven.PomTreeToolbar"), true).getComponent();

    JPanel content = new JPanel(new BorderLayout());
    content.add(new JScrollPane(myTree), BorderLayout.CENTER);
    content.add(myStatusPanel, BorderLayout.SOUTH);

    setToolbar(toolbar);
    setContent(content);

    myTree.addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        final String id = getMenuId(getSelectedNodes(MavenProjectsStructure.CustomNode.class));
        if (id != null) {
          final ActionGroup actionGroup = (ActionGroup)ActionManager.getInstance().getAction(id);
          if (actionGroup != null) {
            ActionManager.getInstance().createActionPopupMenu("", actionGroup).getComponent().show(comp, x, y);
          }
        }
      }

      @Nullable
      private String getMenuId(Collection<? extends MavenProjectsStructure.CustomNode> nodes) {
        String id = null;
        for (MavenProjectsStructure.CustomNode node : nodes) {
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
    if (dataId.equals(PlatformDataKeys.HELP_ID.getName())) return "reference.toolWindows.mavenProjects";

    if (dataId.equals(PlatformDataKeys.PROJECT.getName())) return myProject;

    if (dataId.equals(PlatformDataKeys.VIRTUAL_FILE.getName())) return extractVirtualFile();
    if (dataId.equals(PlatformDataKeys.VIRTUAL_FILE_ARRAY.getName())) return extractVirtualFiles();

    if (dataId.equals(Location.LOCATION)) return extractLocation();
    if (dataId.equals(PlatformDataKeys.NAVIGATABLE_ARRAY.getName())) return extractNavigatables();

    if (dataId.equals(MavenDataKeys.MAVEN_PROJECT_NODES.getName())) return extractPomNodes();
    if (dataId.equals(MavenDataKeys.MAVEN_GOALS_KEY.getName())) return extractGoals();
    if (dataId.equals(MavenDataKeys.MAVEN_PROFILES_KEY.getName())) return extractProfiles();

    return null;
  }

  private List<MavenProject> extractPomNodes() {
    List<MavenProject> result = new ArrayList<MavenProject>();
    for (MavenProjectsStructure.PomNode each : getSelectedPomNodes()) {
      result.add(each.getMavenProject());
    }
    return result.isEmpty() ? null : result;
  }

  private VirtualFile extractVirtualFile() {
    final MavenProjectsStructure.PomNode pomNode = getContextPomNode();
    if (pomNode == null) return null;
    VirtualFile file = pomNode.getFile();
    if (file == null || !file.isValid()) return null;
    return file;
  }

  private Object extractVirtualFiles() {
    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    for (MavenProjectsStructure.PomNode pomNode : getSelectedPomNodes()) {
      VirtualFile file = pomNode.getFile();
      if (file.isValid()) {
        files.add(file);
      }
    }
    return files.isEmpty() ? null : files.toArray(new VirtualFile[files.size()]);
  }

  private Object extractLocation() {
    VirtualFile file = extractVirtualFile();
    List<String> goals = extractGoals();
    if (file == null || goals == null) return null;

    return new MavenGoalLocation(myProject, file, extractGoals());
  }

  private Object extractNavigatables() {
    final List<Navigatable> navigatables = new ArrayList<Navigatable>();
    for (MavenProjectsStructure.PomNode pomNode : getSelectedPomNodes()) {
      final Navigatable navigatable = pomNode.getNavigatable();
      if (navigatable != null) {
        navigatables.add(navigatable);
      }
    }
    return navigatables.isEmpty() ? null : navigatables.toArray(new Navigatable[navigatables.size()]);
  }

  private List<String> extractGoals() {
    final MavenProjectsStructure.PomNode pomNode = getSelectedPomNode();
    if (pomNode != null) {
      MavenProject project = pomNode.getMavenProject();
      String goal = project.getDefaultGoal();
      if (!StringUtil.isEmptyOrSpaces(goal)) {
        return Collections.singletonList(goal);
      }
    }
    else {
      final List<MavenProjectsStructure.GoalNode> nodes = getSelectedNodes(MavenProjectsStructure.GoalNode.class);
      if (MavenProjectsStructure.getCommonParent(nodes) == null) {
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
      profiles.add(node.getProfile());
    }
    return profiles;
  }

  private <T extends SimpleNode> List<T> getSelectedNodes(final Class<T> aClass) {
    return MavenProjectsStructure.getSelectedNodes(myTree, aClass);
  }

  private List<MavenProjectsStructure.PomNode> getSelectedPomNodes() {
    return getSelectedNodes(MavenProjectsStructure.PomNode.class);
  }

  @Nullable
  private MavenProjectsStructure.PomNode getSelectedPomNode() {
    final List<MavenProjectsStructure.PomNode> pomNodes = getSelectedPomNodes();
    return pomNodes.size() == 1 ? pomNodes.get(0) : null;
  }

  @Nullable
  private MavenProjectsStructure.PomNode getContextPomNode() {
    final MavenProjectsStructure.PomNode pomNode = getSelectedPomNode();
    if (pomNode != null) {
      return pomNode;
    }
    else {
      return MavenProjectsStructure.getCommonParent(getSelectedNodes(MavenProjectsStructure.CustomNode.class));
    }
  }

  private int getStandardGoalOrder(String goal) {
    if (standardGoalOrder == null) {
      standardGoalOrder = new HashMap<String, Integer>();
      int i = 0;
      for (String aGoal : MavenEmbedderFactory.getStandardGoalsList()) {
        standardGoalOrder.put(aGoal, i++);
      }
    }
    Integer order = standardGoalOrder.get(goal);
    return order != null ? order.intValue() : standardGoalOrder.size();
  }
}
