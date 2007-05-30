package org.jetbrains.idea.maven.navigator;

import com.intellij.ProjectTopics;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenDataKeys;
import org.jetbrains.idea.maven.core.util.FileFinder;
import org.jetbrains.idea.maven.core.util.IdeaAPIHelper;
import org.jetbrains.idea.maven.core.util.MavenEnv;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.repo.MavenRepository;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

@State(name = "MavenProjectNavigator", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenProjectNavigator extends PomTreeStructure implements FileWatcher, PersistentStateComponent<MavenNavigatorState> {

  public static MavenProjectNavigator getInstance(Project project) {
    return project.getComponent(MavenProjectNavigator.class);
  }

  @NonNls private static final String MAVEN_NAVIGATOR_TOOLWINDOW_ID = "Maven projects";

  private final Icon myIcon = IconLoader.getIcon("/images/mavenEmblem.png");

  private MavenNavigatorState settings = new MavenNavigatorState();

  private final SimpleTreeBuilder treeBuilder;
  private final SimpleTree tree;
  private NavigatorPanel navigatorPanel;
  private boolean rebuildOnShow = true;

  private Map<VirtualFile, PomNode> fileToNode = new HashMap<VirtualFile, PomNode>();

  private Map<String, Integer> standardGoalOrder;

  public MavenProjectNavigator(Project project, MavenRepository repository) {
    super(project, repository);

    tree = new SimpleTree();
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    tree.addMouseListener(new PomTreePopupHandler());

    treeBuilder = new SimpleTreeBuilder(tree, (DefaultTreeModel)tree.getModel(), this, null);
    treeBuilder.initRoot();
    Disposer.register(project, treeBuilder);

    StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
      public void run() {
        createToolWindow();
        createListeners();
      }
    });
  }

  public boolean isRelevant(VirtualFile file) {
    return file.getName().equalsIgnoreCase(MavenEnv.POM_FILE);
  }

  public void update(VirtualFile file) {
    final PomNode pomNode = fileToNode.get(file);
    if (pomNode != null) {
      pomNode.onFileUpdate();
    }
    else {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile != null) {
        final PomNode newNode = new PomNode(file);
        fileToNode.put(file, newNode);
        root.addToStructure(newNode);
      }
    }
  }

  public void remove(VirtualFile file) {
    final PomNode pomNode = fileToNode.get(file);
    if (pomNode != null) {
      fileToNode.remove(file);
      pomNode.removeFromParent();
    }
  }

  void rebuild() {
    Collection<VirtualFile> files = FileFinder.findFilesByName(ProjectRootManager.getInstance(project).getContentRoots(), MavenEnv.POM_FILE,
                                                               new ArrayList<VirtualFile>(), myFileIndex, null, true);

    final Map<VirtualFile, PomNode> oldFileToNode = fileToNode;
    fileToNode = new HashMap<VirtualFile, PomNode>();
    for (VirtualFile pomFile : files) {
      PomNode pomNode = oldFileToNode.get(pomFile);
      if (pomNode == null) {
        pomNode = new PomNode(pomFile);
      }
      fileToNode.put(pomFile, pomNode);
    }

    restorePluginState();

    updateFromRoot(true, true);
  }

  private void requestRebuild() {
    if (navigatorPanel.isShowing()) {
      rebuildOnShow = false;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (project.isInitialized() && project.isOpen()) {
            rebuild();
          }
        }
      });
    }
    else {
      rebuildOnShow = true;
    }
  }

  private void createToolWindow() {
    navigatorPanel = new NavigatorPanel();

    ToolWindow pomToolWindow = ToolWindowManager.getInstance(project)
      .registerToolWindow(MAVEN_NAVIGATOR_TOOLWINDOW_ID, navigatorPanel, ToolWindowAnchor.RIGHT, project);
    pomToolWindow.setIcon(myIcon);

    new UiNotifyConnector(navigatorPanel, new Activatable() {
      public void showNotify() {
        if (rebuildOnShow) {
          requestRebuild();
        }
      }

      public void hideNotify() {
      }
    });
  }

  private void createListeners() {
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiFileChangeWatcher(this));

    project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        requestRebuild();
      }
    });
  }

  public PomTreeViewSettings getTreeViewSettings() {
    return settings;
  }

  public Map<String, Set<MavenId>> getExtraPluginState() {
    return settings.extraPlugins;
  }

  public MavenNavigatorState getState() {
    savePluginsState();
    return settings;
  }

  public void loadState(MavenNavigatorState state) {
    settings = state;
  }

  protected Collection<PomNode> getAllPomNodes() {
    return fileToNode.values();
  }

  protected void updateTreeFrom(SimpleNode node) {
    final DefaultMutableTreeNode mutableTreeNode = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)tree.getModel().getRoot(), node);
    if (mutableTreeNode != null) {
      treeBuilder.addSubtreeToUpdate(mutableTreeNode);
    }
    else {
      updateFromRoot(false, false);
    }
  }

  protected ProjectDocument.Project loadProjectDocument(final PsiFile psiFile) {
    return MavenProjectDomFileDescription.parse(psiFile);
  }

  public void updateFromRoot(boolean rebuild, boolean restructure) {
    if (restructure) {
      final Collection<PomNode> allPomNodes = getAllPomNodes();
      for (PomNode node : allPomNodes) {
        node.unlinkNested();
      }
      root.rebuild(allPomNodes);
    }
    treeBuilder.updateFromRoot(rebuild);
    if (rebuild) {
      tree.expandPath(new TreePath(tree.getModel().getRoot()));
    }
  }

  private static PomNode getCommonParent(Collection<GoalNode> goalNodes) {
    PomNode parent = null;
    for (GoalNode goalNode : goalNodes) {
      PomNode nextParent = goalNode.getParent(PomNode.class);
      if (parent == null) {
        parent = nextParent;
      }
      else if (parent != nextParent) {
        return null;
      }
    }
    return parent;
  }

  private int getStandardGoalOrder(String goal) {
    if (standardGoalOrder == null) {
      standardGoalOrder = new HashMap<String, Integer>();
      int i = 0;
      for (String aGoal : standardGoals) {
        standardGoalOrder.put(aGoal, i++);
      }
    }
    Integer order = standardGoalOrder.get(goal);
    return order != null ? order : standardGoalOrder.size();
  }

  private List<String> getSortedGoals(Collection<GoalNode> goalNodes) {
    List<String> goalList = new ArrayList<String>();
    for (GoalNode node : goalNodes) {
      goalList.add(node.getGoal());
    }
    Collections.sort(goalList, new Comparator<String>() {
      public int compare(String o1, String o2) {
        return getStandardGoalOrder(o1) - getStandardGoalOrder(o2);
      }
    });
    return goalList;
  }

  Collection<SimpleNode> getSelectedNodes() {
    Collection<SimpleNode> nodes = new ArrayList<SimpleNode>();
    TreePath[] treePaths = tree.getSelectionPaths();
    if (treePaths != null) {
      for (TreePath treePath : treePaths) {
        nodes.add(tree.getNodeFor(treePath));
      }
    }
    return nodes;
  }

  public <T extends SimpleNode> Collection<T> getSelectedNodes(Class<T> aClass, boolean strict) {
    return filterNodes(getSelectedNodes(), aClass, strict);
  }

  <T extends SimpleNode> Collection<T> filterNodes(Collection<SimpleNode> nodes, Class<T> aClass, boolean strict) {
    Collection<T> filtered = new ArrayList<T>();
    for (SimpleNode node : nodes) {
      if ((aClass != null) && (!aClass.isInstance(node) || (strict && aClass != node.getClass()))) {
        filtered.clear();
        break;
      }
      //noinspection unchecked
      filtered.add((T)node);
    }
    return filtered;
  }

  Navigatable[] getNavigatables() {
    Collection<MavenProjectNavigator.PomNode> selectedNodes = getSelectedNodes(MavenProjectNavigator.PomNode.class, true);
    if (selectedNodes.isEmpty()) {
      return null;
    }
    else {
      final ArrayList<Navigatable> navigatables = new ArrayList<Navigatable>();
      for (MavenProjectNavigator.PomNode pomNode : selectedNodes) {
        navigatables.add(pomNode.getPsiFile());
      }
      return navigatables.toArray(new Navigatable[navigatables.size()]);
    }
  }

  String getActionId(Collection<? extends PomTreeStructure.CustomNode> nodes, boolean group) {
    String id = null;
    for (PomTreeStructure.CustomNode node : nodes) {
      String menuId = node.getActionId(group);
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

  class NavigatorPanel extends JPanel implements DataProvider {

    public NavigatorPanel() {
      setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));

      add(ActionManager.getInstance().createActionToolbar("New Maven Toolbar", (ActionGroup)ActionManager.getInstance()
        .getAction("Maven.PomTreeToolbar"), true).getComponent(),
          new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, GridConstraints
            .SIZEPOLICY_CAN_SHRINK | GridConstraints
            .SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 20), null));

      add(new JScrollPane(tree), new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_SOUTH, GridConstraints.FILL_BOTH,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null,
                                                     null, null));
    }

    @Nullable
    public Object getData(@NonNls String dataId) {
      if (dataId.equals(DataKeys.PROJECT.getName())) {
        return project;
      }
      if (dataId.equals(DataKeys.NAVIGATABLE_ARRAY.getName())) {
        return getNavigatables();
      }
      if (dataId.equals(DataKeys.VIRTUAL_FILE.getName())) {
        final Collection<PomNode> pomNodes = getSelectedNodes(PomNode.class, false);
        if (pomNodes.size() == 1) {
          return pomNodes.iterator().next().getFile();
        }
        else {
          final PomNode commonParent = getCommonParent(getSelectedNodes(GoalNode.class, false));
          return commonParent == null ? null : commonParent.getFile();
        }
      }
      if (dataId.equals(MavenDataKeys.MAVEN_GOALS_KEY.getName())) {
        final Collection<PomNode> pomNodes = getSelectedNodes(PomNode.class, false);
        if (pomNodes.size() == 1) {
          return Collections.singletonList("install");
        }
        else {
          return getSortedGoals(getSelectedNodes(PomTreeStructure.GoalNode.class, false));
        }
      }
      return null;
    }
  }

  public class PomTreePopupHandler extends PopupHandler {

    public void invokePopup(Component comp, int x, int y) {
      final String id = getActionIdForSelection(true);
      if (id != null) {
        final ActionGroup actionGroup = (ActionGroup)ActionManager.getInstance().getAction(id);
        if (actionGroup != null) {
          ActionManager.getInstance().createActionPopupMenu("", actionGroup).getComponent().show(comp, x, y);
        }
      }
    }

    public void mouseReleased(MouseEvent e) {
      super.mouseReleased(e);
      if ((e.getClickCount() == 2) && (e.getButton() == MouseEvent.BUTTON1)) {
        final String actionId = getActionIdForSelection(false);
        if (actionId != null) {
          IdeaAPIHelper.executeAction(actionId, e);
        }
      }
    }

    private String getActionIdForSelection(final boolean group) {
      return getActionId(getSelectedNodes(PomTreeStructure.CustomNode.class, false), group);
    }
  }
}
