package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiManager;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.IdeaAPIHelper;
import org.jetbrains.idea.maven.core.util.MavenEnv;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.repo.MavenRepository;
import org.jetbrains.idea.maven.repo.PluginDocument;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.io.File;
import java.util.*;
import java.util.List;

public abstract class PomTreeStructure extends SimpleTreeStructure {
  final ProjectFileIndex myFileIndex;

  final Project project;

  protected MavenProjectsState myProjectsState;

  private final MavenRepository myRepository;

  final RootNode root = new RootNode();

  // TODO : update tree after local repository location change

  private final Collection<String> standardPhases = MavenEnv.getStandardPhasesList();
  final Collection<String> standardGoals = MavenEnv.getStandardGoalsList();

  private static final Icon iconPom = IconLoader.getIcon("/images/mavenProject.png");
  private static final Icon iconPhase = IconLoader.getIcon("/images/phase.png");
  private static final Icon iconPhasesOpen = IconLoader.getIcon("/images/phasesOpen.png");
  private static final Icon iconPhasesClosed = IconLoader.getIcon("/images/phasesClosed.png");
  private static final Icon iconPlugin = IconLoader.getIcon("/images/mavenPlugin.png");
  private static final Icon iconPluginGoal = IconLoader.getIcon("/images/pluginGoal.png");
  private static final Icon iconFolderOpen = IconLoader.getIcon("/images/nestedPomsOpen.png");
  private static final Icon iconFolderClosed = IconLoader.getIcon("/images/nestedPomsClosed.png");
  private static final Icon iconProfilesOpen = IconLoader.getIcon("/images/profilesOpen.png");
  private static final Icon iconProfilesClosed = IconLoader.getIcon("/images/profilesClosed.png");
  private static final Icon iconProfileActive = IconLoader.getIcon("/images/profileActive.png");
  private static final Icon iconProfileInactive = IconLoader.getIcon("/images/profileInactive.png");

  public PomTreeStructure(Project project, MavenProjectsState cache, MavenRepository repository) {
    this.project = project;
    myProjectsState = cache;
    myRepository = repository;
    myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
  }

  public MavenRepository getRepository() {
    return myRepository;
  }

  public Object getRootElement() {
    return root;
  }

  protected abstract PomTreeViewSettings getTreeViewSettings();

  protected abstract Map<String, Set<MavenId>> getExtraPluginState();

//  protected abstract ProjectDocument.Project loadProjectDocument(VirtualFile file);

  protected abstract Iterable<? extends PomNode> getAllPomNodes();

  protected abstract void updateTreeFrom(SimpleNode node);

  private static final Comparator<SimpleNode> nodeComparator = new Comparator<SimpleNode>() {
    public int compare(SimpleNode o1, SimpleNode o2) {
      return o1.getName().compareToIgnoreCase(o2.getName());
    }
  };

  private static <T extends SimpleNode> void insertSorted(List<T> list, T newObject) {
    int pos = Collections.binarySearch(list, newObject, nodeComparator);
    list.add(pos >= 0 ? pos : -pos - 1, newObject);
  }

  class CustomNode extends SimpleNode {
    private CustomNode structuralParent;

    public CustomNode(CustomNode parent) {
      super(project);
      setStructuralParent(parent);
    }

    public void setStructuralParent(CustomNode structuralParent) {
      this.structuralParent = structuralParent;
    }

    public SimpleNode[] getChildren() {
      return SimpleNode.NO_CHILDREN;
    }

    public <T extends CustomNode> T getParent(Class<T> aClass) {
      CustomNode node = this;
      while (true) {
        node = node.structuralParent;
        if (node == null || aClass.isInstance(node)) {
          //noinspection unchecked
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

  interface DisplayList {
    void add(Iterable<? extends CustomNode> nodes);

    void add(CustomNode node);

    void insert(CustomNode node);

    void sort();
  }

  abstract class ListNode extends CustomNode {

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

    protected abstract void displayChildren(DisplayList displayList);
  }

  class RootNode extends PomGroupNode {

    final List<ModuleNode> moduleNodes = new ArrayList<ModuleNode>();

    public RootNode() {
      super(null);
      addPlainText(NavigatorBundle.message("node.root"));
    }

    protected void displayChildren(DisplayList displayList) {
      displayList.add(moduleNodes);
      displayList.add(pomNodes);
      displayList.sort();
    }

    protected CustomNode getVisibleParent() {
      return null;
    }

    void rebuild(final Collection<PomNode> allPomNodes) {
      moduleNodes.clear();
      pomNodes.clear();
      for (PomNode pomNode : allPomNodes) {
        addToStructure(pomNode);
      }
    }

    void addToStructure(PomNode pomNode) {
      if (!getTreeViewSettings().showIgnored && myProjectsState.isIgnored(pomNode.virtualFile)) {
        return;
      }
      if (getTreeViewSettings().groupByModule) {
        findModuleNode(myFileIndex.getModuleForFile(pomNode.getFile())).addUnder(pomNode);
      }
      else {
        addUnder(pomNode);
      }
    }

    private ModuleNode findModuleNode(Module module) {
      for (ModuleNode moduleNode : moduleNodes) {
        if (moduleNode.getModule() == module) {
          return moduleNode;
        }
      }
      ModuleNode newNode = new ModuleNode(this, module);
      insertSorted(moduleNodes, newNode);
      updateSubTree();
      return newNode;
    }
  }

  abstract class PomGroupNode extends ListNode {

    final List<PomNode> pomNodes = new ArrayList<PomNode>();

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

      Collection<PomNode> childrenOfNew = new ArrayList<PomNode>();
      for (PomNode node : pomNodes) {
        if (newNode.isAncestor(node)) {
          childrenOfNew.add(node);
        }
      }

      pomNodes.removeAll(childrenOfNew);

      for (PomNode child : childrenOfNew) {
        newNode.addNestedPom(child);
      }

      add(newNode);
      return true;
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

    protected abstract CustomNode getVisibleParent();
  }

  class ModuleNode extends PomGroupNode {
    private final Module module;

    public ModuleNode(RootNode parent, Module module) {
      super(parent);
      this.module = module;
      if (module != null) {
        addPlainText(module.getName());
        ModuleType moduleType = module.getModuleType();
        setIcons(moduleType.getNodeIcon(false), moduleType.getNodeIcon(true));
      }
    }

    public Module getModule() {
      return module;
    }

    boolean isVisible() {
      return super.isVisible() && getTreeViewSettings().groupByModule;
    }

    protected void display(DisplayList displayList) {
      super.display(displayList);
      if (!isVisible()) {
        displayChildren(displayList);
        displayList.sort();
      }
    }

    protected CustomNode getVisibleParent() {
      return root;
    }
  }

  public class PomNode extends ListNode {

    final private VirtualFile virtualFile;
    String savedPath = "";

    private MavenProject mavenProject;

    final GoalGroupNode lifecycleNode;
    final ProfilesNode profilesNode;
    final NestedPomsNode modulePomsNode;
    final NestedPomsNode nonModulePomsNode;

    final List<PluginNode> pomPluginNodes = new ArrayList<PluginNode>();
    final List<ExtraPluginNode> extraPluginNodes = new ArrayList<ExtraPluginNode>();

    public PomNode(VirtualFile virtualFile) {
      super(null);
      this.virtualFile = virtualFile;

      lifecycleNode = new LifecycleNode(this);
      profilesNode = new ProfilesNode(this);
      modulePomsNode = new ModulePomsNode(this);
      nonModulePomsNode = new NonModulePomsNode(this);

      setUniformIcon(iconPom);

      updateNode();
      restorePlugins();
    }

    protected void display(DisplayList displayList) {
      displayList.insert(this);
      if (!modulePomsNode.isVisible()) {
        modulePomsNode.displayChildren(displayList);
      }
      if (!nonModulePomsNode.isVisible()) {
        nonModulePomsNode.displayChildren(displayList);
      }
    }

    protected void displayChildren(DisplayList displayList) {
      displayList.add(lifecycleNode);
      displayList.add(profilesNode);
      displayList.add(pomPluginNodes);
      displayList.add(extraPluginNodes);
      displayList.add(modulePomsNode);
      displayList.add(nonModulePomsNode);
    }

    @Nullable
    @NonNls
    protected String getActionId() {
      return "Maven.Run";
    }

    @Nullable
    @NonNls
    protected String getMenuId() {
      return "Maven.PomMenu";
    }

    public String getId() {
      if (mavenProject == null) {
        return NavigatorBundle.message("node.pom.invalid");
      }

      final String name = mavenProject.getName();
      if (!StringUtil.isEmptyOrSpaces(name)) {
        return name;
      }

      final String artifactId = mavenProject.getArtifactId();
      if (!StringUtil.isEmptyOrSpaces(artifactId)) {
        return artifactId;
      }

      return NavigatorBundle.message("node.pom.unnamed");
    }

    public List<ExtraPluginNode> getExtraPluginNodes() {
      return extraPluginNodes;
    }

    public String getSavedPath() {
      return savedPath;
    }

    public VirtualFile getFile() {
      return virtualFile;
    }

    private VirtualFile getDirectory() {
      return getFile().getParent();
    }

    public boolean isAncestor(PomNode that) {
      return VfsUtil.isAncestor(this.getDirectory(), that.getDirectory(), true);
    }

    public Navigatable getNavigatable() {
      return PsiManager.getInstance(project).findFile(virtualFile);
    }

    private void updateNode() {
      mavenProject = myProjectsState.getMavenProject(virtualFile);
      createProfileNodes();
      createPomPluginNodes();
      regroupNested();
      updateText();
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

    private void createProfileNodes() {
      profilesNode.clear();
      for (String id : ProjectUtil.collectProfileIds(mavenProject, new TreeSet<String>())) {
        profilesNode.add(id);
      }
      profilesNode.updateActive(myProjectsState.getProfiles(virtualFile), true);
    }

    private void createPomPluginNodes() {
      pomPluginNodes.clear();
      for (MavenId mavenId : ProjectUtil.collectPluginIds(mavenProject, myProjectsState.getProfiles(virtualFile), new HashSet<MavenId>())) {
        addPlugin(mavenId);
      }
      addPlugin(new MavenId("org.apache.maven.plugins", "maven-site-plugin", null));
    }

    private void addPlugin(final MavenId mavenId) {
      PluginDocument pluginDocument = getRepository().loadPlugin(mavenId);
      insertSorted(pomPluginNodes,
                   pluginDocument != null ? new ValidPluginNode(this, pluginDocument.getPlugin()) : new InvalidPluginNode(this, mavenId));
    }

    public void attachPlugin(final PluginDocument pluginDocument) {
      final ExtraPluginNode pluginNode = doAttachPlugin(pluginDocument);

      Set<MavenId> pluginIds = getExtraPluginState().get(savedPath);
      if (pluginIds == null) {
        pluginIds = new HashSet<MavenId>();
        getExtraPluginState().put(savedPath, pluginIds);
      }
      pluginIds.add(pluginNode.getId());
    }

    private ExtraPluginNode doAttachPlugin(final PluginDocument pluginDocument) {
      final ExtraPluginNode pluginNode = new ExtraPluginNode(this, pluginDocument.getPlugin());
      extraPluginNodes.add(pluginNode);
      updateSubTree();
      return pluginNode;
    }

    private void restorePlugins() {
      final Set<MavenId> pluginIds = getExtraPluginState().get(savedPath);
      if (pluginIds != null) {
        for (MavenId pluginId : pluginIds) {
          if (!hasPlugin(pluginId)) {
            final PluginDocument pluginDocument = getRepository().loadPlugin(pluginId);
            if (pluginDocument != null) {
              doAttachPlugin(pluginDocument);
            }
          }
        }
      }
    }

    public void detachPlugin(ExtraPluginNode pluginNode) {
      extraPluginNodes.remove(pluginNode);
      updateSubTree();

      final Set<MavenId> pluginIds = getExtraPluginState().get(savedPath);
      if (pluginIds != null) {
        pluginIds.remove(pluginNode.getId());
      }
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted"})
    public boolean hasPlugin(final MavenId id) {
      for (PluginNode node : pomPluginNodes) {
        if (node.getId().matches(id)) {
          return true;
        }
      }
      for (PluginNode node : extraPluginNodes) {
        if (node.getId().matches(id)) {
          return true;
        }
      }
      return false;
    }

    private void updateText() {
      clearColoredText();
      if (myProjectsState.isIgnored(virtualFile)) {
        addColoredFragment(getId(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, Color.GRAY));
      }
      else {
        addPlainText(getId());
      }
      savedPath = getDirectory().getPath();
      addColoredFragment(" (" + savedPath + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    public void addNestedPom(PomNode child) {
      if (!modulePomsNode.addUnder(child)) {
        nonModulePomsNode.addUnder(child);
      }
    }

    void onFileUpdate() {
      final String oldName = getName();
      final String oldPath = getSavedPath();

      updateNode();

      if (!oldPath.equals(getSavedPath())) {
        removeFromParent();
        root.addToStructure(this);
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
      return getParent(getTreeViewSettings().groupByDirectory
                       ? NonModulePomsNode.class
                       : getTreeViewSettings().groupByModule ? ModuleNode.class : RootNode.class);
    }

    void removeFromParent() {
      getParent(PomGroupNode.class).remove(this);
    }

    public void unlinkNested() {
      modulePomsNode.clear();
      nonModulePomsNode.clear();
    }

    public boolean containsAsModule(final PomNode node) {
      if (mavenProject != null) {
        String relPath =
          FileUtil.getRelativePath(new File(virtualFile.getPath()).getParentFile(), new File(node.virtualFile.getPath()).getParentFile());
        if (relPath != null) {
          for (String moduleName : ProjectUtil
            .collectModuleNames(mavenProject, myProjectsState.getProfiles(virtualFile), new HashSet<String>())) {
            if (relPath.equals(moduleName)) {
              return true;
            }
          }
        }
      }
      return false;
    }

    public void setIgnored(boolean on) {
      updateText();
      if (getTreeViewSettings().showIgnored) {
        updateSubTree();
      }
      else {
        if (on) {
          removeFromParent();
        }
        else {
          root.addToStructure(this);
        }
      }
    }

    public void setProfiles(final Collection<String> profiles) {
      profilesNode.updateActive(profiles, false);
      regroupNested();
      createPomPluginNodes();
      updateSubTree();
    }
  }

  class NestedPomsNode extends PomGroupNode {

    public NestedPomsNode(PomNode parent) {
      super(parent);
      setIcons(iconFolderClosed, iconFolderOpen);
    }

    boolean isVisible() {
      return super.isVisible() && getTreeViewSettings().groupByDirectory;
    }

    protected CustomNode getVisibleParent() {
      return getParent(PomNode.class);
    }
  }

  class ModulePomsNode extends NestedPomsNode {

    public ModulePomsNode(PomNode parent) {
      super(parent);
      addPlainText(NavigatorBundle.message("node.modules"));
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

  class NonModulePomsNode extends NestedPomsNode {

    public NonModulePomsNode(PomNode parent) {
      super(parent);
      addPlainText(NavigatorBundle.message("node.nested.poms"));
    }
  }

  abstract class GoalGroupNode extends ListNode {

    final List<CustomNode> goalNodes = new ArrayList<CustomNode>();

    public GoalGroupNode(PomNode parent) {
      super(parent);
    }

    protected void displayChildren(DisplayList displayList) {
      displayList.add(goalNodes);
    }
  }

  public abstract class GoalNode extends CustomNode {
    private final String goal;

    public GoalNode(CustomNode parent, String goal) {
      super(parent);
      this.goal = goal;
      addPlainText(goal);
      setUniformIcon(iconPhase);
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

  class LifecycleNode extends GoalGroupNode {

    public LifecycleNode(PomNode parent) {
      super(parent);
      addPlainText(NavigatorBundle.message("node.lifecycle"));
      setIcons(iconPhasesClosed, iconPhasesOpen);

      for (String goal : standardGoals) {
        goalNodes.add(new StandardGoalNode(this, goal));
      }
    }
  }

  class StandardGoalNode extends GoalNode {

    public StandardGoalNode(CustomNode parent, String goal) {
      super(parent, goal);
    }

    public boolean isVisible() {
      return !getTreeViewSettings().filterStandardPhases || standardPhases.contains(getName());
    }
  }

  private class ProfilesNode extends ListNode {

    final List<ProfileNode> profileNodes = new ArrayList<ProfileNode>();

    public ProfilesNode(final PomNode parent) {
      super(parent);
      addPlainText(NavigatorBundle.message("node.profiles"));
      setIcons(iconProfilesClosed, iconProfilesOpen);
    }

    boolean isVisible() {
      return !profileNodes.isEmpty();
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
      addPlainText(name);
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

      clearColoredText();
      addPlainText(name);
      addColoredFragment(active ? NavigatorBundle.message("node.profile.active") : NavigatorBundle.message("node.profile.inactive"),
                         SimpleTextAttributes.GRAY_ATTRIBUTES);
      setUniformIcon(active ? iconProfileActive : iconProfileInactive);
    }
  }

  public class PluginNode extends GoalGroupNode {
    private final MavenId myId;

    public PluginNode(PomNode parent, final MavenId id) {
      super(parent);
      myId = id;
      setUniformIcon(iconPlugin);
    }

    public MavenId getId() {
      return myId;
    }
  }

  public class InvalidPluginNode extends PluginNode {
    public InvalidPluginNode(PomNode parent, MavenId id) {
      super(parent, id);
      addColoredFragment(id.toString(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, Color.red));
    }
  }

  public class ValidPluginNode extends PluginNode {
    public ValidPluginNode(PomNode parent, final PluginDocument.Plugin plugin) {
      super(parent, new MavenId(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion()));
      addPlainText(plugin.getGoalPrefix());
      addColoredFragment(" ("+ getId().toString() + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
      for (PluginDocument.Mojo mojo : plugin.getMojos().getMojoList()) {
        goalNodes.add(new PluginGoalNode(this, plugin.getGoalPrefix(), mojo.getGoal()));
      }
    }
  }

  class PluginGoalNode extends GoalNode {
    public PluginGoalNode(PluginNode parent, String pluginPrefix, String goal) {
      super(parent, pluginPrefix + ":" + goal);
      setUniformIcon(iconPluginGoal);
    }
  }

  public class ExtraPluginNode extends ValidPluginNode {
    public ExtraPluginNode(PomNode parent, final PluginDocument.Plugin plugin) {
      super(parent, plugin);
    }

    public void detach() {
      getParent(PomNode.class).detachPlugin(this);
    }

    @Nullable
    @NonNls
    protected String getMenuId() {
      return "Maven.PluginMenu";
    }
  }
}
