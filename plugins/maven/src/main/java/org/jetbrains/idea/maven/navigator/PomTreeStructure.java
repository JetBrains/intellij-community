package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.MavenEnv;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.repo.MavenRepository;
import org.jetbrains.idea.maven.repo.PluginDocument;

import javax.swing.*;
import java.util.*;

public abstract class PomTreeStructure extends SimpleTreeStructure {
  final ProjectFileIndex myFileIndex;

  final Project project;

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

  public PomTreeStructure(Project project, MavenRepository repository) {
    this.project = project;
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

  protected abstract ProjectDocument.Project loadProjectDocument(PsiFile psiFile);

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

  private PomNode findPomByPath(String path) {
    for (PomNode pomNode : getAllPomNodes()) {
      if (pomNode.getFile().getPath().equals(path)) {
        return pomNode;
      }
    }
    return null;
  }

  void restorePluginState() {
    for (Map.Entry<String, Set<MavenId>> entry : getExtraPluginState().entrySet()) {
      PomNode pomNode = findPomByPath(entry.getKey());
      if (pomNode != null) {
        for (MavenId pluginElement : entry.getValue()) {
          if (!pomNode.hasPlugin(pluginElement)) {
            PluginDocument pluginDocument =
              getRepository().loadPlugin(pluginElement.groupId, pluginElement.artifactId, pluginElement.version);
            if (pluginDocument != null) {
              pomNode.attachPlugin(pluginDocument);
            }
          }
        }
      }
    }
  }

  void savePluginsState() {
    final Map<String, Set<MavenId>> plugins = getExtraPluginState();
    plugins.clear();

    for (PomNode pomNode : getAllPomNodes()) {
      final List<ExtraPluginNode> list = pomNode.getExtraPluginNodes();
      if (!list.isEmpty()) {
        Set<MavenId> set = new HashSet<MavenId>();
        for (ExtraPluginNode pluginNode : list) {
          PluginDocument.Plugin pluginModel = pluginNode.getDocument().getPlugin();
          set.add(new MavenId(pluginModel.getGroupId(), pluginModel.getArtifactId(), pluginModel.getVersion()));
        }
        plugins.put(pomNode.getFile().getPath(), set);
      }
    }
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
    String getActionId(final boolean group) {
      return null;
    }

    void updateSubTree() {
      updateTreeFrom(this);
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

    public void addUnder(PomNode newNode) {
      Collection<PomNode> childrenOfNew = new ArrayList<PomNode>();
      for (PomNode node : pomNodes) {
        if (node.isAncestor(newNode)) {
          node.addNestedPom(newNode);
          return;
        }
        if (newNode.isAncestor(node)) {
          childrenOfNew.add(node);
        }
      }

      pomNodes.removeAll(childrenOfNew);
      for (PomNode child : childrenOfNew) {
        newNode.addNestedPom(child);
      }

      add(newNode);
    }

    private void add(PomNode pomNode) {
      boolean wasVisible = isVisible();

      pomNode.setStructuralParent(this);
      insertSorted(pomNodes, pomNode);

      updateGroupNode(wasVisible);
    }

    public void remove(PomNode pomNode) {
      boolean wasVisible = isVisible();

      pomNode.setStructuralParent(null);
      pomNodes.remove(pomNode);
      merge(pomNode.nestedPomsNode);

      updateGroupNode(wasVisible);
    }

    public void reinsert(PomNode pomNode) {
      pomNodes.remove(pomNode);
      insertSorted(pomNodes, pomNode);
    }

    private void merge(PomGroupNode groupNode) {
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
      else {
        addPlainText("no module");
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
    final private PsiFile psiFile;

    private ProjectDocument.Project projectDocument;

    final GoalGroupNode phasesNode;
    final List<PluginNode> pomPluginNodes = new ArrayList<PluginNode>();

    final List<ExtraPluginNode> extraPluginNodes = new ArrayList<ExtraPluginNode>();
    final NestedPomsNode nestedPomsNode;
    String savedPath = "";

    public PomNode(VirtualFile virtualFile) {
      super(null);
      this.virtualFile = virtualFile;
      this.psiFile = PsiManager.getInstance(project).findFile(virtualFile);

      phasesNode = new StandardPhasesNode(this);
      nestedPomsNode = new NestedPomsNode(this);

      setUniformIcon(iconPom);

      updateNode();
    }

    protected void display(DisplayList displayList) {
      displayList.insert(this);
      if (!nestedPomsNode.isVisible()) {
        nestedPomsNode.displayChildren(displayList);
      }
    }

    protected void displayChildren(DisplayList displayList) {
      displayList.add(phasesNode);
      displayList.add(pomPluginNodes);
      displayList.add(extraPluginNodes);
      displayList.add(nestedPomsNode);
    }

    @Nullable
    @NonNls
    protected String getActionId(final boolean group) {
      return group ? "Maven.PomMenu" : "Maven.Run";
    }

    public String getId() {
      if (projectDocument == null) {
        return "invalid";
      }

      final String name = projectDocument.getName().getStringValue();
      if (!StringUtil.isEmptyOrSpaces(name)) {
        return name;
      }

      final String artifactId = projectDocument.getArtifactId().getStringValue();
      if (!StringUtil.isEmptyOrSpaces(artifactId)) {
        return artifactId;
      }

      return "unnamed";
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

    public PsiFile getPsiFile() {
      return psiFile;
    }

    private void updateNode() {
      projectDocument = psiFile != null ? loadProjectDocument(psiFile) : null;
      createPomPluginNodes();
      updateText();
    }

    private void createPomPluginNodes() {
      pomPluginNodes.clear();

      if (projectDocument != null) {
        for (ProjectDocument.Plugin pomPlugin : projectDocument.getBuild().getPlugins().getPlugins()) {
          PluginDocument pluginDocument = getRepository().loadPlugin(pomPlugin.getGroupId().getStringValue(),
                                                                     pomPlugin.getArtifactId().getStringValue(),
                                                                     pomPlugin.getVersion().getStringValue());
          if (pluginDocument != null) {
            pomPluginNodes.add(new PluginNode(this, pluginDocument));
          }
        }
        // add the site plugin with all its goals
        addAdditionalPlugin("org.apache.maven.plugins", "maven-site-plugin", null);
      }
    }

    private void addAdditionalPlugin(String groupId, String artifactId, String version) {
      PluginDocument pluginDocument = getRepository().loadPlugin(groupId, artifactId, version);
      if (pluginDocument != null) {
          pomPluginNodes.add(new PluginNode(this, pluginDocument));
      }
    }

    private void updateText() {
      clearColoredText();
      addPlainText(getId());
      savedPath = getDirectory().getPath();
      addColoredFragment(" (" + savedPath + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    public void addNestedPom(PomNode child) {
      nestedPomsNode.addUnder(child);
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
                       ? NestedPomsNode.class
                       : getTreeViewSettings().groupByModule ? ModuleNode.class : RootNode.class);
    }

    void removeFromParent() {
      getParent(PomGroupNode.class).remove(this);
    }

    public void attachPlugin(final PluginDocument pluginDocument) {
      extraPluginNodes.add(new ExtraPluginNode(this, pluginDocument));
      updateSubTree();
    }

    public void detachPlugin(ExtraPluginNode pluginNode) {
      extraPluginNodes.remove(pluginNode);
      updateSubTree();
    }

    public void unlinkNested() {
      nestedPomsNode.clear();
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted"})
    public boolean hasPlugin(final MavenId id) {
      for (PluginNode node : pomPluginNodes) {
        if (node.getId().equals(id)) {
          return true;
        }
      }
      for (PluginNode node : extraPluginNodes) {
        if (node.getId().equals(id)) {
          return true;
        }
      }
      return false;
    }
  }

  class NestedPomsNode extends PomGroupNode {

    public NestedPomsNode(PomNode parent) {
      super(parent);
      addPlainText(NavigatorBundle.message("node.nested.poms"));
      setIcons(iconFolderClosed, iconFolderOpen);
    }

    boolean isVisible() {
      return super.isVisible() && getTreeViewSettings().groupByDirectory;
    }

    protected CustomNode getVisibleParent() {
      return getParent(PomNode.class);
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
    protected String getActionId(final boolean group) {
      return group ? "Maven.GoalMenu" : "Maven.Run";
    }
  }

  class StandardPhasesNode extends GoalGroupNode {

    public StandardPhasesNode(PomNode parent) {
      super(parent);
      addPlainText(NavigatorBundle.message("node.phases"));
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

  public class PluginNode extends GoalGroupNode {
    final PluginDocument pluginDocument;

    public PluginNode(PomNode parent, final PluginDocument pluginDocument) {
      super(parent);
      this.pluginDocument = pluginDocument;
      String prefix = this.pluginDocument.getPlugin().getGoalPrefix();
      addPlainText(prefix);
      setUniformIcon(iconPlugin);

      for (PluginDocument.Mojo mojo : this.pluginDocument.getPlugin().getMojos().getMojoList()) {
        goalNodes.add(new PluginGoalNode(this, prefix, mojo.getGoal()));
      }
    }

    public MavenId getId() {
      final PluginDocument.Plugin plugin = pluginDocument.getPlugin();
      return new MavenId(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion());
    }
  }

  class PluginGoalNode extends GoalNode {
    public PluginGoalNode(PluginNode parent, String pluginPrefix, String goal) {
      super(parent, pluginPrefix + ":" + goal);
      setUniformIcon(iconPluginGoal);
    }
  }

  public class ExtraPluginNode extends PluginNode {
    public ExtraPluginNode(PomNode parent, final PluginDocument pluginDocument) {
      super(parent, pluginDocument);
    }

    public void detach() {
      getParent(PomNode.class).detachPlugin(this);
    }

    public PluginDocument getDocument() {
      return pluginDocument;
    }

    @Nullable
    @NonNls
    protected String getActionId(final boolean group) {
      return group ? "Maven.PluginMenu" : null;
    }
  }
}
