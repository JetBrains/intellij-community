package org.jetbrains.idea.maven.navigator;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiManager;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.IdeaAPIHelper;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.MavenPluginInfo;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.events.MavenEventsManager;
import org.jetbrains.idea.maven.core.util.MavenArtifactUtil;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectModelProblem;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.*;
import java.util.List;

public abstract class MavenTreeStructure extends SimpleTreeStructure {
  private static final Icon MAVEN_PROJECT_ICON = IconLoader.getIcon("/images/mavenProject.png");

  private static final Icon OPEN_PROFILES_ICON = IconLoader.getIcon("/images/profilesOpen.png");
  private static final Icon CLOSED_PROFILES_ICON = IconLoader.getIcon("/images/profilesClosed.png");

  private static final Icon OPEN_PHASES_ICON = IconLoader.getIcon("/images/phasesOpen.png");
  private static final Icon CLOSED_PHASES_ICON = IconLoader.getIcon("/images/phasesClosed.png");
  private static final Icon PHASE_ICON = IconLoader.getIcon("/images/phase.png");

  private static final Icon OPEN_PLUGINS_ICON = IconLoader.getIcon("/images/phasesOpen.png");
  private static final Icon CLOSED_PLUGINS_ICON = IconLoader.getIcon("/images/phasesClosed.png");
  private static final Icon PLUGIN_ICON = IconLoader.getIcon("/images/mavenPlugin.png");
  private static final Icon PLUGIN_GOAL_ICON = IconLoader.getIcon("/images/pluginGoal.png");

  private static final Icon OPEN_MODULES_ICON = IconLoader.getIcon("/images/modulesOpen.png");
  private static final Icon CLOSED_MODULES_ICON = IconLoader.getIcon("/images/modulesClosed.png");

  protected final Project myProject;
  protected final MavenProjectsManager myProjectsManager;
  protected final MavenEventsManager myEventsHandler;

  protected final RootNode myRoot = new RootNode();

  protected final Collection<String> myStandardPhases = MavenEmbedderFactory.getStandardPhasesList();
  protected final Collection<String> myStandardGoals = MavenEmbedderFactory.getStandardGoalsList();

  public MavenTreeStructure(Project project,
                            MavenProjectsManager projectsManager,
                            MavenEventsManager eventsHandler) {
    myProject = project;
    myProjectsManager = projectsManager;
    myEventsHandler = eventsHandler;
  }

  public Object getRootElement() {
    return myRoot;
  }

  protected abstract PomTreeViewSettings getTreeViewSettings();

  protected abstract void updateTreeFrom(@Nullable SimpleNode node);

  protected boolean isMinimalView() {
    return false;
  }

  private static final Comparator<SimpleNode> nodeComparator = new Comparator<SimpleNode>() {
    public int compare(SimpleNode o1, SimpleNode o2) {
      if (o1 instanceof ProfilesNode) return -1;
      if (o2 instanceof ProfilesNode) return 1;
      return getRepr(o1).compareToIgnoreCase(getRepr(o2));
    }

    private String getRepr(SimpleNode node) {
      return node.getName();
    }
  };

  private static <T extends SimpleNode> void insertSorted(List<T> list, T newObject) {
    int pos = Collections.binarySearch(list, newObject, nodeComparator);
    list.add(pos >= 0 ? pos : -pos - 1, newObject);
  }

  public static List<SimpleNode> getSelectedNodes(SimpleTree tree) {
    List<SimpleNode> nodes = new ArrayList<SimpleNode>();
    TreePath[] treePaths = tree.getSelectionPaths();
    if (treePaths != null) {
      for (TreePath treePath : treePaths) {
        nodes.add(tree.getNodeFor(treePath));
      }
    }
    return nodes;
  }

  public static <T extends SimpleNode> List<T> getSelectedNodes(SimpleTree tree, Class<T> aClass) {
    final List<T> filtered = new ArrayList<T>();
    for (SimpleNode node : getSelectedNodes(tree)) {
      if ((aClass != null) && (!aClass.isInstance(node))) {
        filtered.clear();
        break;
      }
      //noinspection unchecked
      filtered.add((T)node);
    }
    return filtered;
  }

  @Nullable
  public static PomNode getCommonParent(Collection<? extends CustomNode> goalNodes) {
    PomNode parent = null;
    for (CustomNode node : goalNodes) {
      PomNode nextParent = node.getParent(PomNode.class);
      if (parent == null) {
        parent = nextParent;
      }
      else if (parent != nextParent) {
        return null;
      }
    }
    return parent;
  }

  public void selectNode(SimpleTreeBuilder builder, SimpleNode node) {
    builder.select(node, null);
  }

  protected String formatHtmlImage(String path) {
    return "<img src=\"" + getClass().getResource(path) + "\"> ";
  }

  public enum ErrorLevel {
    NONE, WARNING, ERROR
  }

  public abstract class CustomNode extends SimpleNode {
    private CustomNode structuralParent;
    private ErrorLevel myNodeErrorLevel = ErrorLevel.NONE;
    private String myDescription;

    public CustomNode(CustomNode parent) {
      super(parent);
      setStructuralParent(parent);
    }

    public void setStructuralParent(CustomNode structuralParent) {
      this.structuralParent = structuralParent;
    }

    @Override
    public NodeDescriptor getParentDescriptor() {
      return structuralParent;
    }

    public SimpleNode[] getChildren() {
      return SimpleNode.NO_CHILDREN;
    }

    public <T extends CustomNode> T getParent(Class<T> aClass) {
      CustomNode node = this;
      while (true) {
        node = node.structuralParent;
        if (node == null || aClass.isInstance(node)) {
          //noinspection unchecked,ConstantConditions
          return (T)node;
        }
      }
    }

    boolean isVisible() {
      return true;
    }

    void display(DisplayList list) {
      if (isVisible()) {
        list.insert(this);
      }
    }

    public ErrorLevel getOverallErrorLevel() {
      ErrorLevel childrenErrorLevel = getChildrenErrorLevel();
      return childrenErrorLevel.compareTo(myNodeErrorLevel) > 0
             ? childrenErrorLevel
             : myNodeErrorLevel;
    }

    public ErrorLevel getChildrenErrorLevel() {
      ErrorLevel result = ErrorLevel.NONE;
      for (SimpleNode each : getChildren()) {
        ErrorLevel eachLevel = ((CustomNode)each).getOverallErrorLevel();
        if (eachLevel.compareTo(result) > 0) result = eachLevel;
      }
      return result;
    }

    public ErrorLevel getNodeErrorLevel() {
      return myNodeErrorLevel;
    }

    public void setNodeErrorLevel(ErrorLevel level) {
      if (myNodeErrorLevel == level) return;
      myNodeErrorLevel = level;

      CustomNode each = this;
      while (each != null) {
        each.updateNameAndDescription();
        each = each.structuralParent;
      }
    }

    protected abstract void updateNameAndDescription();

    @Override
    protected void doUpdate() {
      super.doUpdate();
      updateNameAndDescription();
    }

    protected void setName(String name) {
      setName(name, (String)null);
    }

    protected void setName(String name, @Nullable String hint) {
      setName(name, getPlainAttributes());
      if (hint != null) {
        addColoredFragment(" (" + hint + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }

    protected void setName(String name, SimpleTextAttributes attribs) {
      clearColoredText();
      addColoredFragment(name, prepareAttribs(attribs));
    }

    private SimpleTextAttributes prepareAttribs(SimpleTextAttributes from) {
      ErrorLevel level = getOverallErrorLevel();
      Color waveColor = level == ErrorLevel.NONE
                        ? null : (level == ErrorLevel.WARNING ? Color.GRAY : Color.RED);
      int style = from.getStyle();
      if (waveColor != null) style |= SimpleTextAttributes.STYLE_WAVED;
      return new SimpleTextAttributes(from.getBgColor(),
                                      from.getFgColor(),
                                      waveColor,
                                      style);
    }

    public String getDescription() {
      return myDescription;
    }

    public void setDescription(String text) {
      myDescription = text;
    }

    @Nullable
    @NonNls
    String getActionId() {
      return null;
    }

    @Nullable
    @NonNls
    String getMenuId() {
      return null;
    }

    void updateSubTree() {
      updateTreeFrom(this);
    }

    public void handleDoubleClickOrEnter(final SimpleTree tree, final InputEvent inputEvent) {
      final String actionId = getActionId();
      if (actionId != null) {
        IdeaAPIHelper.executeAction(actionId, inputEvent);
      }
    }
  }

  public interface DisplayList {
    void add(Iterable<? extends CustomNode> nodes);

    void add(CustomNode node);

    void insert(CustomNode node);

    void sort();
  }

  public abstract class ListNode extends CustomNode {
    final List<SimpleNode> displayList = new ArrayList<SimpleNode>();

    final DisplayList myDisplayList = new DisplayList() {
      public void insert(CustomNode node) {
        displayList.add(node);
      }

      public void sort() {
        Collections.sort(displayList, nodeComparator);
      }


      public void add(Iterable<? extends CustomNode> nodes) {
        for (CustomNode node : nodes) {
          add(node);
        }
      }

      public void add(CustomNode node) {
        node.display(this);
      }
    };

    public ListNode(CustomNode parent) {
      super(parent);
    }

    public SimpleNode[] getChildren() {
      displayList.clear();
      displayChildren(myDisplayList);
      return displayList.toArray(new SimpleNode[displayList.size()]);
    }

    void display(DisplayList list) {
      if (isVisible()) {
        super.display(list);
      } else {
        displayChildren(list);
      }
    }

    protected abstract void displayChildren(DisplayList displayList);
  }

  public abstract class PomGroupNode extends ListNode {
    public final List<PomNode> pomNodes = new ArrayList<PomNode>();

    public PomGroupNode(CustomNode parent) {
      super(parent);
    }

    boolean isVisible() {
      return !pomNodes.isEmpty();
    }

    protected void displayChildren(DisplayList displayList) {
      displayList.add(pomNodes);
    }

    protected boolean addUnderExisting(final PomNode newNode) {
      for (PomNode node : pomNodes) {
        if (node.isAncestor(newNode)) {
          node.addNestedPom(newNode);
          return true;
        }
      }
      return false;
    }

    public boolean addUnder(PomNode newNode) {
      if (addUnderExisting(newNode)) {
        return true;
      }

      for (PomNode child : removeChildren(newNode, new ArrayList<PomNode>())) {
        newNode.addNestedPom(child);
      }

      add(newNode);
      return true;
    }

    protected Collection<PomNode> removeChildren(final PomNode parent, final Collection<PomNode> children) {
      return removeOwnChildren(parent, children);
    }

    protected final Collection<PomNode> removeOwnChildren(final PomNode parent, final Collection<PomNode> children) {
      for (PomNode node : pomNodes) {
        if (parent.isAncestor(node)) {
          children.add(node);
        }
      }
      pomNodes.removeAll(children);
      return children;
    }

    protected void add(PomNode pomNode) {
      boolean wasVisible = isVisible();

      pomNode.setStructuralParent(this);
      insertSorted(pomNodes, pomNode);

      updateGroupNode(wasVisible);
    }

    public void remove(PomNode pomNode) {
      boolean wasVisible = isVisible();

      pomNode.setStructuralParent(null);
      pomNodes.remove(pomNode);
      adoptOrphans(pomNode);

      updateGroupNode(wasVisible);
    }

    private void adoptOrphans(final PomNode pomNode) {
      PomGroupNode newParent = getNewParentForOrphans();
      newParent.merge(pomNode.modulePomsNode);
      newParent.merge(pomNode.nonModulePomsNode);
      if (newParent != this) {
        updateTreeFrom(newParent);
      }
    }

    protected PomGroupNode getNewParentForOrphans() {
      return this;
    }

    public void reinsert(PomNode pomNode) {
      pomNodes.remove(pomNode);
      insertSorted(pomNodes, pomNode);
    }

    protected void merge(PomGroupNode groupNode) {
      for (PomNode pomNode : groupNode.pomNodes) {
        insertSorted(pomNodes, pomNode);
      }
      groupNode.clear();
    }

    public void clear() {
      pomNodes.clear();
    }

    private void updateGroupNode(boolean wasVisible) {
      if (wasVisible && isVisible()) {
        updateSubTree();
      }
      else {
        updateTreeFrom(getVisibleParent());
      }
    }

    @NotNull
    protected abstract CustomNode getVisibleParent();
  }

  public class RootNode extends PomGroupNode {
    public ProfilesNode profilesNode;

    public RootNode() {
      super(null);
      profilesNode = new ProfilesNode(this);
      updateNameAndDescription();
    }

    protected void updateNameAndDescription() {
      setName(NavigatorBundle.message("node.root"));
    }

    protected void displayChildren(DisplayList displayList) {
      displayList.add(profilesNode);
      displayList.add(pomNodes);
      displayList.sort();
    }

    @NotNull
    protected CustomNode getVisibleParent() {
      return myRoot;
    }

    public void rebuild(final Collection<PomNode> allPomNodes) {
      pomNodes.clear();

      updateProfileNodes();

      for (PomNode node : allPomNodes) {
        node.unlinkNested();
      }
      for (PomNode pomNode : allPomNodes) {
        addToStructure(pomNode);
      }
    }

    void addToStructure(PomNode pomNode) {
      if (!getTreeViewSettings().showIgnored && myProjectsManager.isIgnored(pomNode.myProjectModel)) {
        return;
      }
      addUnder(pomNode);
    }

    public void updateProfileNodes() {
      profilesNode.clear();
      for (String each : myProjectsManager.getAvailableProfiles()) {
        profilesNode.add(each);
      }
      profilesNode.updateActive(myProjectsManager.getActiveProfiles(), true);
      profilesNode.updateSubTree();
    }

    public void setActiveProfiles(List<String> profiles) {
      profilesNode.updateActive(profiles, false);
    }
  }

  public class PomNode extends ListNode {
    final private MavenProjectModel myProjectModel;
    private String savedPath = "";
    private String actionIdPrefix = "";

    private LifecycleNode lifecycleNode;
    private PluginsNode pluginsNode;
    public NestedPomsNode modulePomsNode;
    public NestedPomsNode nonModulePomsNode;

    public PomNode(MavenProjectModel mavenProjectModel) {
      super(null);
      this.myProjectModel = mavenProjectModel;

      lifecycleNode = new LifecycleNode(this);
      pluginsNode = new PluginsNode(this);
      modulePomsNode = new ModulePomsNode(this);
      nonModulePomsNode = new NonModulePomsNode(this);

      modulePomsNode.sibling = nonModulePomsNode;
      nonModulePomsNode.sibling = modulePomsNode;

      updateNode();
      setUniformIcon(MAVEN_PROJECT_ICON);
    }

    public VirtualFile getFile() {
      return myProjectModel.getFile();
    }

    protected void displayChildren(DisplayList displayList) {
      displayList.add(lifecycleNode);
      displayList.add(pluginsNode);
      displayList.add(modulePomsNode);
      displayList.add(nonModulePomsNode);
    }

    @Nullable
    @NonNls
    protected String getMenuId() {
      return "Maven.PomMenu";
    }

    public String getId() {
      final String name = myProjectModel.getProjectName();
      if (!StringUtil.isEmptyOrSpaces(name)) {
        return name;
      }

      final String artifactId = myProjectModel.getMavenProject().getArtifactId();
      if (!StringUtil.isEmptyOrSpaces(artifactId)) {
        return artifactId;
      }

      return NavigatorBundle.message("node.pom.unnamed");
    }

    public MavenProjectModel getProjectModel() {
      return myProjectModel;
    }

    private VirtualFile getDirectory() {
      //noinspection ConstantConditions
      return myProjectModel.getDirectoryFile();
    }

    public boolean isAncestor(PomNode that) {
      return VfsUtil.isAncestor(getDirectory(), that.getDirectory(), true);
    }

    @Nullable
    public Navigatable getNavigatable() {
      return PsiManager.getInstance(MavenTreeStructure.this.myProject).findFile(myProjectModel.getFile());
    }

    private void updateNode() {
      updateErrorLevelAndDescription();
      updateNameAndDescription();

      savedPath = myProjectModel.getFile().getPath();
      actionIdPrefix = myEventsHandler.getActionId(savedPath, null);

      lifecycleNode.updateGoals();
      createPluginsNode();
      regroupNested();
    }

    private void updateErrorLevelAndDescription() {
      List<MavenProjectModelProblem> problems = myProjectModel.getProblems();
      if (problems.isEmpty()) {
        setNodeErrorLevel(ErrorLevel.NONE);
        setDescription("No problem");
      }
      else {
        boolean isError = false;
        for (MavenProjectModelProblem each : problems) {
          if (each.isCritical()) {
            isError = true;
            break;
          }
        }
        setDescription("Problems!!!");
        setNodeErrorLevel(isError ? ErrorLevel.ERROR : ErrorLevel.WARNING);
      }
    }

    @Override
    protected SimpleTextAttributes getPlainAttributes() {
      if (myProjectsManager.isIgnored(myProjectModel)) {
        return new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, Color.GRAY);
      }
      return super.getPlainAttributes();
    }

    @Override
    protected void updateNameAndDescription() {
      setName(getId());
      updateDescription();
    }

    private void updateDescription() {
      StringBuffer desc = new StringBuffer();
      desc.append("<html>");
      desc.append("<table>");
      desc.append("<tr>");
      desc.append("<td>");
      desc.append("  <table>");
      desc.append("  <tr>");
      desc.append("  <td>Project:</td>");
      desc.append("  <td nowrap>" + myProjectModel.getMavenId() + "</td>");
      desc.append("  </tr>");
      desc.append("  <tr>");
      desc.append("  <td>Location:</td>");
      desc.append("  <td nowrap>" + getFile().getPath() + "</td>");
      desc.append("  </tr>");
      desc.append("  </table>");
      desc.append("</td>");
      desc.append("</tr>");
      appendProblems(desc, true);
      appendProblems(desc, false);

      if (getModulesErrorLevel() != ErrorLevel.NONE) {
        desc.append("<tr>");
        desc.append("<td><i>Some modules have problems.</i></td>");
        desc.append("</tr>");
      }

      desc.append("</table>");
      desc.append("</html>");
      setDescription(desc.toString());
    }

    private ErrorLevel getModulesErrorLevel() {
      ErrorLevel result =  ErrorLevel.NONE;
      for (PomNode each : modulePomsNode.pomNodes) {
        ErrorLevel moduleLevel = each.getOverallErrorLevel();
        if (moduleLevel.compareTo(result) > 0) result = moduleLevel;
      }
      return result;
    }

    private void appendProblems(StringBuffer desc, boolean critical) {
      List<MavenProjectModelProblem> problems = collectProblems(critical);
      if (problems.isEmpty()) return;

      desc.append("<tr>");
      desc.append("<td>");
      desc.append("<table>");
      boolean first = true;
      for (MavenProjectModelProblem each : problems) {
        desc.append("<tr>");
        if (first) {
          desc.append("<td valign=top>" + formatHtmlImage(critical ? "/images/error.png" : "/images/warning.png") + "</td>");
          desc.append("<td valign=top>" + (critical ? "Errors" : "Warnings") + ":</td>");
          first = false;
        }
        else {
          desc.append("<td colspan=2></td>");
        }
        desc.append("<td valign=top>" + each.getDescription());
        desc.append("</tr>");
      }
      desc.append("</table>");
      desc.append("</td>");
      desc.append("</tr>");
    }

    private List<MavenProjectModelProblem> collectProblems(boolean critical) {
      List<MavenProjectModelProblem> result = new ArrayList<MavenProjectModelProblem>();
      for (MavenProjectModelProblem each : myProjectModel.getProblems()) {
        if (critical == each.isCritical()) result.add(each);
      }
      return result;
    }

    private void createPluginsNode() {
      pluginsNode.clear();
      for (MavenId mavenId : myProjectModel.getPluginIds()) {
        if (!hasPlugin(mavenId)) {
          pluginsNode.add(new PluginNode(this, mavenId));
        }
      }
    }

    public boolean hasPlugin(final MavenId id) {
      for (PluginNode node : pluginsNode.pluginNodes) {
        if (node.getId().matches(id)) {
          return true;
        }
      }
      return false;
    }

    public void addNestedPom(PomNode child) {
      if (!modulePomsNode.addUnder(child)) {
        nonModulePomsNode.addUnder(child);
      }
    }

    void onFileUpdate() {
      final String oldName = getName();
      final String oldPath = savedPath;

      updateNode();

      if (!oldPath.equals(savedPath)) {
        removeFromParent();
        myRoot.addToStructure(this);
      }
      else if (!oldName.equals(getName())) {
        PomGroupNode groupNode = getParent(PomGroupNode.class);
        groupNode.reinsert(this);
        updateTreeFrom(getVisibleParent());
      }
      else {
        updateSubTree();
      }
    }

    private ListNode getVisibleParent() {
      return getParent(getTreeViewSettings().groupStructurally
                       ? NonModulePomsNode.class
                       : RootNode.class);
    }

    void removeFromParent() {
      // parent can be null if we are trying to remove an excluded node
      // thus not present in structure and has no parent
      PomGroupNode parent = getParent(PomGroupNode.class);
      if (parent != null) parent.remove(this);
    }

    public void unlinkNested() {
      modulePomsNode.clear();
      nonModulePomsNode.clear();
    }

    public boolean containsAsModule(PomNode node) {
      MavenProjectsManager m = MavenProjectsManager.getInstance(MavenTreeStructure.this.myProject);
      return m.isModuleOf(myProjectModel, node.myProjectModel);
    }

    public void setIgnored(boolean on) {
      updateNameAndDescription();
      if (getTreeViewSettings().showIgnored) {
        updateSubTree();
      }
      else {
        if (on) {
          removeFromParent();
        }
        else {
          myRoot.addToStructure(this);
        }
      }
    }

    @Nullable
    public GoalNode findGoalNode(final String goalName) {
      for (GoalNode goalNode : collectGoalNodes()) {
        if (goalNode.getGoal().equals(goalName)) {
          return goalNode;
        }
      }

      return null;
    }

    public void updateShortcuts(@Nullable String actionId) {
      if (actionId == null || actionId.startsWith(actionIdPrefix)) {
        boolean update = false;
        for (GoalNode goalNode : collectGoalNodes()) {
          update = goalNode.updateShortcut(actionId) || update;
        }
        if (update) {
          updateSubTree();
        }
      }
    }

    private Collection<GoalNode> collectGoalNodes() {
      Collection<GoalNode> goalNodes = new ArrayList<GoalNode>(lifecycleNode.goalNodes);

      goalNodes.addAll(lifecycleNode.goalNodes);

      for (PluginNode pluginNode : pluginsNode.pluginNodes) {
        goalNodes.addAll(pluginNode.goalNodes);
      }

      return goalNodes;
    }

    public void regroupNested() {
      regroupMisplaced(modulePomsNode, false);
      regroupMisplaced(nonModulePomsNode, true);
    }

    private void regroupMisplaced(final NestedPomsNode src, final boolean misplacedFlag) {
      Collection<PomNode> misplaced = new ArrayList<PomNode>();
      for (PomNode node : src.pomNodes) {
        if (containsAsModule(node) == misplacedFlag) {
          misplaced.add(node);
        }
      }
      for (PomNode node : misplaced) {
        src.pomNodes.remove(node);
        addNestedPom(node);
      }
      if (!misplaced.isEmpty()) {
        updateSubTree();
      }
    }
  }

  public abstract class NestedPomsNode extends PomGroupNode {
    private PomGroupNode sibling;

    public NestedPomsNode(PomNode parent) {
      super(parent);
      setIcons(CLOSED_MODULES_ICON, OPEN_MODULES_ICON);
    }

    boolean isVisible() {
      return super.isVisible() && getTreeViewSettings().groupStructurally;
    }

    @NotNull
    protected CustomNode getVisibleParent() {
      return getParent(PomNode.class);
    }

    protected Collection<PomNode> removeChildren(final PomNode node, final Collection<PomNode> children) {
      super.removeChildren(node, children);
      if (sibling != null) {
        sibling.removeOwnChildren(node, children);
      }
      return children;
    }
  }

  public class ModulePomsNode extends NestedPomsNode {
    public ModulePomsNode(PomNode parent) {
      super(parent);
      updateNameAndDescription();
    }

    @Override
    protected void updateNameAndDescription() {
      setName(NavigatorBundle.message("node.modules"));
    }

    public boolean addUnder(PomNode newNode) {
      if (getParent(PomNode.class).containsAsModule(newNode)) {
        add(newNode);
        return true;
      }
      return addUnderExisting(newNode);
    }

    protected PomGroupNode getNewParentForOrphans() {
      return getParent(PomNode.class).nonModulePomsNode;
    }
  }

  public class NonModulePomsNode extends NestedPomsNode {
    public NonModulePomsNode(PomNode parent) {
      super(parent);
      updateNameAndDescription();
    }

    @Override
    protected void updateNameAndDescription() {
      setName(NavigatorBundle.message("node.nested.poms"));
    }
  }

  public abstract class GoalGroupNode extends ListNode {
    final List<GoalNode> goalNodes = new ArrayList<GoalNode>();
    private final PomNode pomNode;

    public GoalGroupNode(PomNode parent) {
      super(parent);
      pomNode = parent;
    }

    boolean isVisible() {
      return !isMinimalView();
    }

    protected void displayChildren(DisplayList displayList) {
      displayList.add(goalNodes);
    }
  }

  public abstract class GoalNode extends CustomNode {
    private final String goal;
    private final PomNode pomNode;

    private String actionId;

    public GoalNode(GoalGroupNode parent, String goal) {
      super(parent);
      this.goal = goal;
      pomNode = parent.pomNode;
      updateNameAndDescription();
      setUniformIcon(PHASE_ICON);
    }

    @Override
    protected void updateNameAndDescription() {
      String hint = null;
      actionId = pomNode.actionIdPrefix + goal;
      if (!isMinimalView()) {
        hint = myEventsHandler.getActionDescription(pomNode.savedPath, goal);
      }

      setName(goal, hint);
    }

    @Override
    protected SimpleTextAttributes getPlainAttributes() {
      if (goal.equals(pomNode.myProjectModel.getDefaultGoal())) {
        return new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, getColor());
      }
      return super.getPlainAttributes();
    }

    public boolean updateShortcut(@Nullable String actionId) {
      if (actionId == null || actionId.equals(this.actionId)) {
        updateNameAndDescription();
        return true;
      }
      return false;
    }

    public String getGoal() {
      return goal;
    }

    @Nullable
    @NonNls
    protected String getActionId() {
      return "Maven.Run";
    }

    @Nullable
    @NonNls
    protected String getMenuId() {
      return "Maven.GoalMenu";
    }
  }

  public class LifecycleNode extends GoalGroupNode {
    public LifecycleNode(PomNode parent) {
      super(parent);
      updateNameAndDescription();
      setIcons(CLOSED_PHASES_ICON, OPEN_PHASES_ICON);
    }

    @Override
    protected void updateNameAndDescription() {
      setName(NavigatorBundle.message("node.lifecycle"));
    }

    public void updateGoals() {
      goalNodes.clear();
      for (String goal : myStandardGoals) {
        goalNodes.add(new StandardGoalNode(this, goal));
      }
    }
  }

  public class StandardGoalNode extends GoalNode {
    public StandardGoalNode(GoalGroupNode parent, String goal) {
      super(parent, goal);
    }

    public boolean isVisible() {
      return !getTreeViewSettings().filterStandardPhases || myStandardPhases.contains(getName());
    }
  }

  public class ProfilesNode extends ListNode {
    final List<ProfileNode> profileNodes = new ArrayList<ProfileNode>();

    public ProfilesNode(final CustomNode parent) {
      super(parent);
      updateNameAndDescription();
      setIcons(CLOSED_PROFILES_ICON, OPEN_PROFILES_ICON);
    }

    @Override
    protected void updateNameAndDescription() {
      setName(NavigatorBundle.message("node.profiles"));
    }

    boolean isVisible() {
      return !profileNodes.isEmpty() && !isMinimalView();
    }

    protected void displayChildren(DisplayList displayList) {
      displayList.add(profileNodes);
    }

    public void clear() {
      profileNodes.clear();
    }

    public void add(final String profileName) {
      insertSorted(profileNodes, new ProfileNode(this, profileName));
    }

    public void updateActive(final Collection<String> activeProfiles, boolean force) {
      for (ProfileNode node : profileNodes) {
        final boolean active = activeProfiles.contains(node.getProfile());
        if (active != node.isActive() || force) {
          node.setActive(active);
        }
      }
    }
  }

  public class ProfileNode extends CustomNode {
    private final String name;
    private boolean active;

    public ProfileNode(final ProfilesNode parent, String name) {
      super(parent);
      this.name = name;
      updateNameAndDescription();
    }

    @Override
    protected void updateNameAndDescription() {
      setName(name);
    }

    public String getProfile() {
      return name;
    }

    @Nullable
    @NonNls
    protected String getActionId() {
      return "Maven.ToggleProfile";
    }

    @Nullable
    @NonNls
    protected String getMenuId() {
      return "Maven.ProfileMenu";
    }

    public boolean isActive() {
      return active;
    }

    private void setActive(final boolean active) {
      this.active = active;
    }
  }

  public class PluginsNode extends ListNode {
    final List<PluginNode> pluginNodes = new ArrayList<PluginNode>();

    public PluginsNode(final PomNode parent) {
      super(parent);
      updateNameAndDescription();
      setIcons(CLOSED_PLUGINS_ICON, OPEN_PLUGINS_ICON);
    }

    @Override
    protected void updateNameAndDescription() {
      setName(NavigatorBundle.message("node.plugins"));
    }

    boolean isVisible() {
      return !pluginNodes.isEmpty() && !isMinimalView();
    }

    protected void displayChildren(DisplayList displayList) {
      displayList.add(pluginNodes);
    }

    public void clear() {
      pluginNodes.clear();
    }

    public void add(PluginNode node) {
      insertSorted(pluginNodes, node);
    }
  }

  public class PluginNode extends GoalGroupNode {
    private final MavenId myId;
    private MavenPluginInfo myPluginInfo;

    public PluginNode(PomNode parent, final MavenId id) {
      super(parent);
      myId = id;

      myPluginInfo = MavenArtifactUtil.readPluginInfo(myProjectsManager.getLocalRepository(), myId);
      setNodeErrorLevel(myPluginInfo == null ? ErrorLevel.WARNING : ErrorLevel.NONE);
      updateNameAndDescription();
      setUniformIcon(PLUGIN_ICON);

      if (myPluginInfo != null) {
        for (MavenPluginInfo.Mojo mojo : myPluginInfo.getMojos()) {
          goalNodes.add(new PluginGoalNode(this, mojo.getQualifiedGoal()));
        }
      }
    }

    @Override
    protected void updateNameAndDescription() {
      if (myPluginInfo == null) {
        setName(myId.toString());
      }
      else {
        setName(myPluginInfo.getGoalPrefix(), getId().toString());
      }
    }

    public MavenId getId() {
      return myId;
    }
  }

  public class PluginGoalNode extends GoalNode {
    public PluginGoalNode(PluginNode parent, String goal) {
      super(parent, goal);
      setUniformIcon(PLUGIN_GOAL_ICON);
    }
  }
}
