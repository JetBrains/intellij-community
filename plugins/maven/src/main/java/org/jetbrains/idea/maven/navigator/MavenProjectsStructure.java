// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.navigator;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.wizard.UIWizardUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.psi.xml.XmlElement;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import icons.MavenIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPluginDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProfile;
import org.jetbrains.idea.maven.dom.model.MavenDomProfiles;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomSettingsModel;
import org.jetbrains.idea.maven.dom.plugin.MavenDomMojo;
import org.jetbrains.idea.maven.dom.plugin.MavenDomPluginModel;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.*;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;

import static icons.ExternalSystemIcons.Task;
import static org.jetbrains.idea.maven.navigator.MavenProjectsNavigator.TOOL_WINDOW_PLACE_ID;
import static org.jetbrains.idea.maven.project.MavenProjectBundle.message;

public class MavenProjectsStructure extends SimpleTreeStructure {
  private static final URL ERROR_ICON_URL = MavenProjectsStructure.class.getResource("/general/error.png");
  private static final Collection<String> BASIC_PHASES = MavenConstants.BASIC_PHASES;
  private static final Collection<String> PHASES = MavenConstants.PHASES;

  private static final Comparator<MavenSimpleNode> NODE_COMPARATOR = (o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true);
  private final ExecutorService boundedUpdateService;

  private final Project myProject;
  private final MavenProjectsManager myProjectsManager;
  private final MavenTasksManager myTasksManager;
  private final MavenShortcutsManager myShortcutsManager;
  private final MavenProjectsNavigator myProjectsNavigator;

  private final SimpleTreeBuilder myTreeBuilder;
  private final RootNode myRoot = new RootNode();
  private volatile boolean isUnloading = false;

  private final Map<MavenProject, ProjectNode> myProjectToNodeMapping = new HashMap<>();

  public MavenProjectsStructure(Project project,
                                MavenProjectsManager projectsManager,
                                MavenTasksManager tasksManager,
                                MavenShortcutsManager shortcutsManager,
                                MavenProjectsNavigator projectsNavigator,
                                SimpleTree tree) {
    myProject = project;
    myProjectsManager = projectsManager;
    myTasksManager = tasksManager;
    myShortcutsManager = shortcutsManager;
    myProjectsNavigator = projectsNavigator;
    boundedUpdateService = AppExecutorUtil.createBoundedApplicationPoolExecutor("Maven Plugin Updater", 1);
    project.getMessageBus().simpleConnect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        if(MavenUtil.INTELLIJ_PLUGIN_ID.equals(pluginDescriptor.getPluginId().getIdString())) {
          isUnloading = true;
        }
      }
    });

    configureTree(tree);

    myTreeBuilder = new SimpleTreeBuilder(tree, (DefaultTreeModel)tree.getModel(), this, null) {
      // unique class to simplify search through the logs
    };
    Disposer.register(projectsNavigator, myTreeBuilder);

    myTreeBuilder.initRoot();
    myTreeBuilder.expand(myRoot, null);
  }

  private void configureTree(final SimpleTree tree) {
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);

    MavenUIUtil.installCheckboxRenderer(tree, new MavenUIUtil.CheckboxHandler() {
      @Override
      public void toggle(TreePath treePath, InputEvent e) {
        SimpleNode node = tree.getNodeFor(treePath);
        if (node != null) {
          node.handleDoubleClickOrEnter(tree, e);
        }
      }

      @Override
      public boolean isVisible(Object userObject) {
        return userObject instanceof ProfileNode;
      }

      @Override
      public MavenUIUtil.CheckBoxState getState(Object userObject) {
        MavenProfileKind state = ((ProfileNode)userObject).getState();
        switch (state) {
          case NONE:
            return MavenUIUtil.CheckBoxState.UNCHECKED;
          case EXPLICIT:
            return MavenUIUtil.CheckBoxState.CHECKED;
          case IMPLICIT:
            return MavenUIUtil.CheckBoxState.PARTIAL;
        }
        MavenLog.LOG.error("unknown profile state: " + state);
        return MavenUIUtil.CheckBoxState.UNCHECKED;
      }
    });
  }

  @NotNull
  @Override
  public RootNode getRootElement() {
    return myRoot;
  }

  public void update() {
    List<MavenProject> projects = myProjectsManager.getProjects();
    Set<MavenProject> deleted = new HashSet<>(myProjectToNodeMapping.keySet());
    deleted.removeAll(projects);
    updateProjects(projects, deleted);
  }

  private void updateFrom(SimpleNode node) {
    if (node != null) {
      myTreeBuilder.addSubtreeToUpdateByElement(node);
    }
  }

  private void updateUpTo(SimpleNode node) {
    SimpleNode each = node;
    while (each != null) {
      updateFrom(each);
      each = each.getParent();
    }
  }

  public void updateProjects(List<MavenProject> updated, Collection<MavenProject> deleted) {
    for (MavenProject each : updated) {
      ProjectNode node = findNodeFor(each);
      if (node == null) {
        node = new ProjectNode(each);
        myProjectToNodeMapping.put(each, node);
      }
      doUpdateProject(node);
    }

    for (MavenProject each : deleted) {
      ProjectNode node = myProjectToNodeMapping.remove(each);
      if (node != null) {
        ProjectsGroupNode parent = node.getGroup();
        parent.remove(node);
      }
    }

    myRoot.updateProfiles();
  }

  private void doUpdateProject(ProjectNode node) {
    MavenProject project = node.getMavenProject();

    ProjectsGroupNode newParentNode = myRoot;

    if (myProjectsNavigator.getGroupModules()) {
      MavenProject aggregator = myProjectsManager.findAggregator(project);
      if (aggregator != null) {
        ProjectNode aggregatorNode = findNodeFor(aggregator);
        if (aggregatorNode != null && aggregatorNode.isVisible()) {
          newParentNode = aggregatorNode;
        }
      }
    }

    node.updateProject();
    reconnectNode(node, newParentNode);

    ProjectsGroupNode newModulesParentNode = myProjectsNavigator.getGroupModules() && node.isVisible() ? node : myRoot;
    for (MavenProject each : myProjectsManager.getModules(project)) {
      ProjectNode moduleNode = findNodeFor(each);
      if (moduleNode != null && !moduleNode.getParent().equals(newModulesParentNode)) {
        reconnectNode(moduleNode, newModulesParentNode);
      }
    }
  }

  private void reconnectNode(ProjectNode node, ProjectsGroupNode newParentNode) {
    ProjectsGroupNode oldParentNode = node.getGroup();
    if (oldParentNode == null || !oldParentNode.equals(newParentNode)) {
      if (oldParentNode != null) {
        oldParentNode.remove(node);
      }
      newParentNode.add(node);
    }
    else {
      newParentNode.sortProjects();
    }
  }

  public void updateProfiles() {
    myRoot.updateProfiles();
  }

  public void updateIgnored(List<MavenProject> projects) {
    for (MavenProject each : projects) {
      ProjectNode node = findNodeFor(each);
      if (node == null) continue;
      node.updateIgnored();
    }
  }

  public void accept(SimpleNodeVisitor visitor) {
    ((SimpleTree)myTreeBuilder.getTree()).accept(myTreeBuilder, visitor);
  }

  public void updateGoals() {
    for (ProjectNode each : myProjectToNodeMapping.values()) {
      each.updateGoals();
    }
  }

  public void updateRunConfigurations() {
    for (ProjectNode each : myProjectToNodeMapping.values()) {
      each.updateRunConfigurations();
    }
  }

  public void select(MavenProject project) {
    ProjectNode node = findNodeFor(project);
    if (node != null) select(node);
  }

  public void select(SimpleNode node) {
    myTreeBuilder.select(node, null);
  }

  private ProjectNode findNodeFor(MavenProject project) {
    return myProjectToNodeMapping.get(project);
  }

  private boolean isShown(Class aClass) {
    Class<? extends MavenSimpleNode>[] classes = getVisibleNodesClasses();
    if (classes == null) return true;

    for (Class<? extends MavenSimpleNode> c : classes) {
      if (c == aClass) {
        return true;
      }
    }

    return false;
  }

  enum DisplayKind {
    ALWAYS, NEVER, NORMAL
  }

  protected Class<? extends MavenSimpleNode>[] getVisibleNodesClasses() {
    return null;
  }

  protected boolean showDescriptions() {
    return true;
  }

  protected boolean showOnlyBasicPhases() {
    return myProjectsNavigator.getShowBasicPhasesOnly();
  }

  public static <T extends MavenSimpleNode> List<T> getSelectedNodes(SimpleTree tree, Class<T> nodeClass) {
    final List<T> filtered = new ArrayList<>();
    for (SimpleNode node : getSelectedNodes(tree)) {
      if ((nodeClass != null) && (!nodeClass.isInstance(node))) {
        filtered.clear();
        break;
      }
      //noinspection unchecked
      filtered.add((T)node);
    }
    return filtered;
  }

  private static List<SimpleNode> getSelectedNodes(SimpleTree tree) {
    List<SimpleNode> nodes = new ArrayList<>();
    TreePath[] treePaths = tree.getSelectionPaths();
    if (treePaths != null) {
      for (TreePath treePath : treePaths) {
        nodes.add(tree.getNodeFor(treePath));
      }
    }
    return nodes;
  }

  @Nullable
  public static ProjectNode getCommonProjectNode(Collection<? extends MavenSimpleNode> nodes) {
    ProjectNode parent = null;
    for (MavenSimpleNode node : nodes) {
      ProjectNode nextParent = node.findParent(ProjectNode.class);
      if (parent == null) {
        parent = nextParent;
      }
      else if (parent != nextParent) {
        return null;
      }
    }
    return parent;
  }

  public enum ErrorLevel {
    NONE, ERROR
  }

  public abstract class MavenSimpleNode extends CachingSimpleNode {
    private MavenSimpleNode myParent;
    private ErrorLevel myErrorLevel = ErrorLevel.NONE;
    private ErrorLevel myTotalErrorLevel = null;

    public MavenSimpleNode(MavenSimpleNode parent) {
      super(MavenProjectsStructure.this.myProject, null);
      setParent(parent);
    }

    public void setParent(MavenSimpleNode parent) {
      myParent = parent;
    }

    @Override
    public NodeDescriptor getParentDescriptor() {
      return myParent;
    }

    public <T extends MavenSimpleNode> T findParent(Class<T> parentClass) {
      MavenSimpleNode node = this;
      while (true) {
        node = node.myParent;
        if (node == null || parentClass.isInstance(node)) {
          //noinspection unchecked
          return (T)node;
        }
      }
    }

    public boolean isVisible() {
      return getDisplayKind() != DisplayKind.NEVER;
    }

    public DisplayKind getDisplayKind() {
      Class[] visibles = getVisibleNodesClasses();
      if (visibles == null) return DisplayKind.NORMAL;

      for (Class each : visibles) {
        if (each.isInstance(this)) return DisplayKind.ALWAYS;
      }
      return DisplayKind.NEVER;
    }


    @Override
    protected SimpleNode[] buildChildren() {
      List<? extends MavenSimpleNode> children = doGetChildren();
      if (children.isEmpty()) return NO_CHILDREN;

      List<MavenSimpleNode> result = new ArrayList<>();
      for (MavenSimpleNode each : children) {
        if (each.isVisible()) result.add(each);
      }
      return result.toArray(new MavenSimpleNode[0]);
    }

    protected List<? extends MavenSimpleNode> doGetChildren() {
      return Collections.emptyList();
    }

    @Override
    public void cleanUpCache() {
      super.cleanUpCache();
      myTotalErrorLevel = null;
    }

    protected void childrenChanged() {
      MavenSimpleNode each = this;
      while (each != null) {
        each.cleanUpCache();
        each = (MavenSimpleNode)each.getParent();
      }
      updateUpTo(this);
    }

    public ErrorLevel getTotalErrorLevel() {
      if (myTotalErrorLevel == null) {
        myTotalErrorLevel = calcTotalErrorLevel();
      }
      return myTotalErrorLevel;
    }

    private ErrorLevel calcTotalErrorLevel() {
      ErrorLevel childrenErrorLevel = getChildrenErrorLevel();
      return childrenErrorLevel.compareTo(myErrorLevel) > 0 ? childrenErrorLevel : myErrorLevel;
    }

    public ErrorLevel getChildrenErrorLevel() {
      ErrorLevel result = ErrorLevel.NONE;
      for (SimpleNode each : getChildren()) {
        ErrorLevel eachLevel = ((MavenSimpleNode)each).getTotalErrorLevel();
        if (eachLevel.compareTo(result) > 0) result = eachLevel;
      }
      return result;
    }

    public void setErrorLevel(ErrorLevel level) {
      if (myErrorLevel == level) return;
      myErrorLevel = level;
      updateUpTo(this);
    }

    @Override
    protected void doUpdate() {
      setNameAndTooltip(getName(), null);
    }

    protected void setNameAndTooltip(String name, @Nullable @NlsContexts.Tooltip String tooltip) {
      setNameAndTooltip(name, tooltip, (String)null);
    }

    protected void setNameAndTooltip(String name, @Nullable @NlsContexts.Tooltip String tooltip, @Nullable String hint) {
      setNameAndTooltip(name, tooltip, getPlainAttributes());
      if (showDescriptions() && !StringUtil.isEmptyOrSpaces(hint)) {
        addColoredFragment(" (" + hint + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }

    protected void setNameAndTooltip(String name, @Nullable @NlsContexts.Tooltip String tooltip, SimpleTextAttributes attributes) {
      clearColoredText();
      addColoredFragment(name, prepareAttributes(attributes));
      getTemplatePresentation().setTooltip(tooltip);
    }

    private SimpleTextAttributes prepareAttributes(SimpleTextAttributes from) {
      ErrorLevel level = getTotalErrorLevel();
      Color waveColor = level == ErrorLevel.NONE ? null : JBColor.RED;
      int style = from.getStyle();
      if (waveColor != null) style |= SimpleTextAttributes.STYLE_WAVED;
      return new SimpleTextAttributes(from.getBgColor(), from.getFgColor(), waveColor, style);
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

    @Nullable
    public VirtualFile getVirtualFile() {
      return null;
    }

    @Nullable
    public Navigatable getNavigatable() {
      return MavenNavigationUtil.createNavigatableForPom(getProject(), getVirtualFile());
    }

    @Override
    public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
      String actionId = getActionId();
      if (actionId != null) {
        MavenUIUtil.executeAction(actionId, TOOL_WINDOW_PLACE_ID, inputEvent);
      }
    }
  }

  public abstract class GroupNode extends MavenSimpleNode {
    public GroupNode(MavenSimpleNode parent) {
      super(parent);
    }

    @Override
    public boolean isVisible() {
      if (getDisplayKind() == DisplayKind.ALWAYS) return true;

      for (SimpleNode each : getChildren()) {
        if (((MavenSimpleNode)each).isVisible()) return true;
      }
      return false;
    }

    protected <T extends MavenSimpleNode> void insertSorted(List<T> list, T newObject) {
      int pos = Collections.binarySearch(list, newObject, NODE_COMPARATOR);
      list.add(pos >= 0 ? pos : -pos - 1, newObject);
    }

    protected void sort(List<? extends MavenSimpleNode> list) {
      list.sort(NODE_COMPARATOR);
    }
  }

  public abstract class ProjectsGroupNode extends GroupNode {
    private final List<ProjectNode> myProjectNodes = new ArrayList<>();

    public ProjectsGroupNode(MavenSimpleNode parent) {
      super(parent);
      getTemplatePresentation().setIcon(MavenIcons.ModulesClosed);
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
      return myProjectNodes;
    }

    @TestOnly
    public List<ProjectNode> getProjectNodesInTests() {
      return myProjectNodes;
    }

    protected void add(ProjectNode projectNode) {
      projectNode.setParent(this);
      insertSorted(myProjectNodes, projectNode);

      childrenChanged();
    }

    public void remove(ProjectNode projectNode) {
      projectNode.setParent(null);
      myProjectNodes.remove(projectNode);

      childrenChanged();
    }

    public void sortProjects() {
      sort(myProjectNodes);
      childrenChanged();
    }
  }

  public class RootNode extends ProjectsGroupNode {
    private final ProfilesNode myProfilesNode;

    public RootNode() {
      super(null);
      myProfilesNode = new ProfilesNode(this);
    }

    @Override
    public boolean isVisible() {
      return true;
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
      return ContainerUtil.concat(Collections.singletonList(myProfilesNode), super.doGetChildren());
    }

    public void updateProfiles() {
      myProfilesNode.updateProfiles();
    }
  }

  public class ProfilesNode extends GroupNode {
    private List<ProfileNode> myProfileNodes = new ArrayList<>();

    public ProfilesNode(MavenSimpleNode parent) {
      super(parent);
      setUniformIcon(MavenIcons.ProfilesClosed);
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
      return myProfileNodes;
    }

    @Override
    public String getName() {
      return message("view.node.profiles");
    }

    public void updateProfiles() {
      Collection<Pair<String, MavenProfileKind>> profiles = myProjectsManager.getProfilesWithStates();

      List<ProfileNode> newNodes = new ArrayList<>(profiles.size());
      for (Pair<String, MavenProfileKind> each : profiles) {
        ProfileNode node = findOrCreateNodeFor(each.first);
        node.setState(each.second);
        newNodes.add(node);
      }

      myProfileNodes = newNodes;
      sort(myProfileNodes);
      childrenChanged();
    }

    private ProfileNode findOrCreateNodeFor(String profileName) {
      for (ProfileNode each : myProfileNodes) {
        if (each.getProfileName().equals(profileName)) return each;
      }
      return new ProfileNode(this, profileName);
    }
  }

  public class ProfileNode extends MavenSimpleNode {
    private final String myProfileName;
    private MavenProfileKind myState;

    public ProfileNode(ProfilesNode parent, String profileName) {
      super(parent);
      myProfileName = profileName;
    }

    @Override
    public String getName() {
      return myProfileName;
    }

    public String getProfileName() {
      return myProfileName;
    }

    public MavenProfileKind getState() {
      return myState;
    }

    private void setState(MavenProfileKind state) {
      myState = state;
    }

    @Override
    @Nullable
    @NonNls
    protected String getActionId() {
      return "Maven.ToggleProfile";
    }

    @Nullable
    @Override
    public Navigatable getNavigatable() {
      if (myProject == null) return null;
      final List<MavenDomProfile> profiles = new ArrayList<>();

      // search in "Per User Maven Settings" - %USER_HOME%/.m2/settings.xml
      // and in "Global Maven Settings" - %M2_HOME%/conf/settings.xml
      for (VirtualFile virtualFile : myProjectsManager.getGeneralSettings().getEffectiveSettingsFiles()) {
        if (virtualFile != null) {
          final MavenDomSettingsModel model = MavenDomUtil.getMavenDomModel(myProject, virtualFile, MavenDomSettingsModel.class);
          if (model != null) {
            addProfiles(profiles, model.getProfiles().getProfiles());
          }
        }
      }

      for (MavenProject mavenProject : myProjectsManager.getProjects()) {
        // search in "Profile descriptors" - located in project basedir (profiles.xml)
        final VirtualFile mavenProjectFile = mavenProject.getFile();
        final VirtualFile profilesXmlFile = MavenUtil.findProfilesXmlFile(mavenProjectFile);
        if (profilesXmlFile != null) {
          final MavenDomProfiles profilesModel = MavenDomUtil.getMavenDomProfilesModel(myProject, profilesXmlFile);
          if (profilesModel != null) {
            addProfiles(profiles, profilesModel.getProfiles());
          }
        }

        // search in "Per Project" - Defined in the POM itself (pom.xml)
        final MavenDomProjectModel projectModel = MavenDomUtil.getMavenDomProjectModel(myProject, mavenProjectFile);
        if (projectModel != null) {
          addProfiles(profiles, projectModel.getProfiles().getProfiles());
        }
      }
      return getNavigatable(profiles);
    }

    private Navigatable getNavigatable(@NotNull final List<MavenDomProfile> profiles) {
      if (profiles.size() > 1) {
        return new NavigatableAdapter() {
          @Override
          public void navigate(final boolean requestFocus) {
            JBPopupFactory.getInstance()
              .createPopupChooserBuilder(profiles)
              .setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList list,
                                                              Object value,
                                                              int index,
                                                              boolean isSelected,
                                                              boolean cellHasFocus) {
                  Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                  MavenDomProfile mavenDomProfile = (MavenDomProfile)value;
                  XmlElement xmlElement = mavenDomProfile.getXmlElement();
                  if (xmlElement != null) {
                    setText(xmlElement.getContainingFile().getVirtualFile().getPresentableUrl());
                  }
                  return result;
                }
              })
              .setTitle(message("maven.notification.choose.file.to.open"))
              .setItemChosenCallback((value) -> {
                final Navigatable navigatable = getNavigatable(value);
                if (navigatable != null) navigatable.navigate(requestFocus);
              }).createPopup().showInFocusCenter();
          }
        };
      }
      else {
        return getNavigatable(ContainerUtil.getFirstItem(profiles));
      }
    }

    @Nullable
    private Navigatable getNavigatable(@Nullable final MavenDomProfile profile) {
      if (profile == null) return null;
      XmlElement xmlElement = profile.getId().getXmlElement();
      return xmlElement instanceof Navigatable ? (Navigatable)xmlElement : null;
    }

    private void addProfiles(@NotNull List<MavenDomProfile> result, @Nullable List<MavenDomProfile> profilesToAdd) {
      if (profilesToAdd == null) return;
      for (MavenDomProfile profile : profilesToAdd) {
        if (StringUtil.equals(profile.getId().getValue(), myProfileName)) {
          result.add(profile);
        }
      }
    }
  }

  public class ProjectNode extends ProjectsGroupNode {
    private final MavenProject myMavenProject;
    private final LifecycleNode myLifecycleNode;
    private final PluginsNode myPluginsNode;
    private final DependenciesNode myDependenciesNode;
    private final RunConfigurationsNode myRunConfigurationsNode;

    private @NlsContexts.Tooltip String myTooltipCache;

    public ProjectNode(@NotNull MavenProject mavenProject) {
      super(null);
      myMavenProject = mavenProject;

      myLifecycleNode = new LifecycleNode(this);
      myPluginsNode = new PluginsNode(this);
      myDependenciesNode = new DependenciesNode(this, mavenProject);
      myRunConfigurationsNode = new RunConfigurationsNode(this);

      getTemplatePresentation().setIcon(MavenIcons.MavenProject);
    }

    public MavenProject getMavenProject() {
      return myMavenProject;
    }

    public ProjectsGroupNode getGroup() {
      return (ProjectsGroupNode)super.getParent();
    }

    @Override
    public boolean isVisible() {
      if (!myProjectsNavigator.getShowIgnored() && myProjectsManager.isIgnored(myMavenProject)) return false;
      return super.isVisible();
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
      return ContainerUtil.concat(
        Arrays.asList(myLifecycleNode, myPluginsNode, myRunConfigurationsNode, myDependenciesNode),
        super.doGetChildren()
      );
    }

    private void updateProject() {
      setErrorLevel(myMavenProject.getProblems().isEmpty() ? ErrorLevel.NONE : ErrorLevel.ERROR);
      myLifecycleNode.updateGoalsList();
      myPluginsNode.updatePlugins(myMavenProject);

      if (isShown(DependencyNode.class)) {
        myDependenciesNode.updateDependencies();
      }

      myRunConfigurationsNode.updateRunConfigurations(myMavenProject);

      myTooltipCache = makeDescription();

      updateFrom(getParent());
    }

    public void updateIgnored() {
      getGroup().childrenChanged();
    }

    public void updateGoals() {
      updateFrom(myLifecycleNode);
      updateFrom(myPluginsNode);
    }

    public void updateRunConfigurations() {
      myRunConfigurationsNode.updateRunConfigurations(myMavenProject);
      updateFrom(myRunConfigurationsNode);
    }

    @Override
    public String getName() {
      if (myProjectsNavigator.getAlwaysShowArtifactId()) {
        return myMavenProject.getMavenId().getArtifactId();
      }
      else {
        return myMavenProject.getDisplayName();
      }
    }

    @Override
    protected void doUpdate() {
      String hint = null;

      if (!myProjectsNavigator.getGroupModules()
          && myProjectsManager.findAggregator(myMavenProject) == null
          && myProjectsManager.getProjects().size() > myProjectsManager.getRootProjects().size()) {
        hint = "root";
      }

      setNameAndTooltip(getName(), myTooltipCache, hint);
    }

    @Override
    protected SimpleTextAttributes getPlainAttributes() {
      if (myProjectsManager.isIgnored(myMavenProject)) {
        return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY);
      }
      return super.getPlainAttributes();
    }

    @NlsContexts.DetailedDescription
    private String makeDescription() {
      StringBuilder desc = new StringBuilder();

      desc.append("<html>")
        .append("<table>");

      desc.append("<tr>")
        .append("<td nowrap>").append("<table>")
        .append("<tr>")
        .append("<td nowrap>").append(message("detailed.description.project")).append("</td>")
        .append("<td nowrap>").append(myMavenProject.getMavenId()).append("</td>")
        .append("</tr>")
        .append("<tr>")
        .append("<td nowrap>").append(message("detailed.description.location")).append("</td>")
        .append("<td nowrap>").append(UIWizardUtil.getPresentablePath(myMavenProject.getPath())).append("</td>")
        .append("</tr>")
        .append("</table>").append("</td>")
        .append("</tr>");

      appendProblems(desc);

      desc.append("</table>")
        .append("</html>");

      return desc.toString(); //NON-NLS
    }

    private void appendProblems(StringBuilder desc) {
      List<MavenProjectProblem> problems = myMavenProject.getProblems();
      if (problems.isEmpty()) return;

      desc.append("<tr>" +
                  "<td nowrap>" +
                  "<table>");

      boolean first = true;
      for (MavenProjectProblem each : problems) {
        desc.append("<tr>");
        if (first) {
          desc.append("<td nowrap valign=top>").append(MavenUtil.formatHtmlImage(ERROR_ICON_URL)).append("</td>");
          desc.append("<td nowrap valign=top>").append(message("detailed.description.problems")).append("</td>");
          first = false;
        }
        else {
          desc.append("<td nowrap colspan=2></td>");
        }
        desc.append("<td nowrap valign=top>").append(wrappedText(each)).append("</td>");
        desc.append("</tr>");
      }
      desc.append("</table>" +
                  "</td>" +
                  "</tr>");
    }

    private String wrappedText(MavenProjectProblem each) {
      String description = ObjectUtils.chooseNotNull(each.getDescription(), each.getPath());
      if (description == null) return "";

      String text = StringUtil.replace(description, Arrays.asList("<", ">"), Arrays.asList("&lt;", "&gt;"));
      StringBuilder result = new StringBuilder();
      int count = 0;
      for (int i = 0; i < text.length(); i++) {
        char ch = text.charAt(i);
        result.append(ch);

        if (count++ > 80) {
          if (ch == ' ') {
            count = 0;
            result.append("<br>");
          }
        }
      }
      return result.toString();
    }

    @Override
    public VirtualFile getVirtualFile() {
      return myMavenProject.getFile();
    }

    @Override
    protected void setNameAndTooltip(String name, @Nullable String tooltip, SimpleTextAttributes attributes) {
      super.setNameAndTooltip(name, tooltip, attributes);
      if (myProjectsNavigator.getShowVersions()) {
        addColoredFragment(":" + myMavenProject.getMavenId().getVersion(),
                           new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY));
      }
    }

    @Override
    @Nullable
    @NonNls
    protected String getMenuId() {
      return "Maven.NavigatorProjectMenu";
    }
  }

  public class ModulesNode extends ProjectsGroupNode {
    public ModulesNode(ProjectNode parent) {
      super(parent);
      getTemplatePresentation().setIcon(MavenIcons.ModulesClosed);
    }

    @Override
    public String getName() {
      return message("view.node.modules");
    }
  }

  public abstract class GoalsGroupNode extends GroupNode {
    protected final List<GoalNode> myGoalNodes = new ArrayList<>();

    public GoalsGroupNode(MavenSimpleNode parent) {
      super(parent);
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
      return myGoalNodes;
    }
  }

  public abstract class GoalNode extends MavenSimpleNode {
    private final MavenProject myMavenProject;
    private final String myGoal;
    private final String myDisplayName;

    public GoalNode(GoalsGroupNode parent, String goal, String displayName) {
      super(parent);
      myMavenProject = findParent(ProjectNode.class).getMavenProject();
      myGoal = goal;
      myDisplayName = displayName;
      setUniformIcon(Task);
    }

    public MavenProject getMavenProject() {
      return myMavenProject;
    }

    public String getProjectPath() {
      return myMavenProject.getPath();
    }

    public String getGoal() {
      return myGoal;
    }

    @Override
    public String getName() {
      return myDisplayName;
    }

    @Override
    protected void doUpdate() {
      String s1 = StringUtil.nullize(myShortcutsManager.getDescription(myMavenProject, myGoal));
      String s2 = StringUtil.nullize(myTasksManager.getDescription(myMavenProject, myGoal));

      String hint;
      if (s1 == null) {
        hint = s2;
      }
      else if (s2 == null) {
        hint = s1;
      }
      else {
        hint = s1 + ", " + s2;
      }

      setNameAndTooltip(getName(), null, hint);
    }

    @Override
    protected SimpleTextAttributes getPlainAttributes() {
      SimpleTextAttributes original = super.getPlainAttributes();

      int style = original.getStyle();
      Color color = original.getFgColor();
      boolean custom = false;

      if ("test".equals(myGoal) && MavenRunner.getInstance(myProject).getSettings().isSkipTests()) {
        color = SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor();
        style |= SimpleTextAttributes.STYLE_STRIKEOUT;
        custom = true;
      }
      if (myGoal.equals(myMavenProject.getDefaultGoal())) {
        style |= SimpleTextAttributes.STYLE_BOLD;
        custom = true;
      }
      if (custom) return original.derive(style, color, null, null);
      return original;
    }

    @Override
    @Nullable
    @NonNls
    protected String getActionId() {
      return "Maven.RunBuild";
    }

    @Override
    @Nullable
    @NonNls
    protected String getMenuId() {
      return "Maven.BuildMenu";
    }
  }

  public class LifecycleNode extends GoalsGroupNode {
    public LifecycleNode(ProjectNode parent) {
      super(parent);

      for (String goal : PHASES) {
        myGoalNodes.add(new StandardGoalNode(this, goal));
      }
      getTemplatePresentation().setIcon(AllIcons.Nodes.ConfigFolder);
    }

    @Override
    public String getName() {
      return message("view.node.lifecycle");
    }

    public void updateGoalsList() {
      childrenChanged();
    }
  }

  public class StandardGoalNode extends GoalNode {
    public StandardGoalNode(GoalsGroupNode parent, String goal) {
      super(parent, goal, goal);
    }

    @Override
    public boolean isVisible() {
      if (showOnlyBasicPhases() && !BASIC_PHASES.contains(getGoal())) return false;
      return super.isVisible();
    }
  }

  public class PluginsNode extends GroupNode {
    private final List<PluginNode> myPluginNodes = new ArrayList<>();

    public PluginsNode(ProjectNode parent) {
      super(parent);
      getTemplatePresentation().setIcon(AllIcons.Nodes.ConfigFolder);
    }

    @Override
    public String getName() {
      return message("view.node.plugins");
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
      return myPluginNodes;
    }

    public void updatePlugins(MavenProject mavenProject) {

      List<MavenPlugin> plugins = mavenProject.getDeclaredPlugins();
      boundedUpdateService.execute(new UpdatePluginsTreeTask(this, plugins));
    }
  }

  public class PluginNode extends GoalsGroupNode {
    private final MavenPlugin myPlugin;
    private MavenPluginInfo myPluginInfo;

    public PluginNode(PluginsNode parent, MavenPlugin plugin, MavenPluginInfo pluginInfo) {
      super(parent);
      myPlugin = plugin;

      getTemplatePresentation().setIcon(MavenIcons.MavenPlugin);
      updatePlugin(pluginInfo);
    }

    public MavenPlugin getPlugin() {
      return myPlugin;
    }

    @Override
    public String getName() {
      return myPluginInfo == null ? myPlugin.getDisplayString() : myPluginInfo.getGoalPrefix();
    }

    @Override
    protected void doUpdate() {
      setNameAndTooltip(getName(), null, myPluginInfo != null ? myPlugin.getDisplayString() : null);
    }

    public void updatePlugin(@Nullable MavenPluginInfo newPluginInfo) {
      boolean hadPluginInfo = myPluginInfo != null;

      myPluginInfo = newPluginInfo;
      boolean hasPluginInfo = myPluginInfo != null;

      setErrorLevel(myPluginInfo == null ? ErrorLevel.ERROR : ErrorLevel.NONE);

      if (hadPluginInfo == hasPluginInfo) return;

      myGoalNodes.clear();
      if (myPluginInfo != null) {
        for (MavenPluginInfo.Mojo mojo : myPluginInfo.getMojos()) {
          myGoalNodes.add(new PluginGoalNode(this, mojo.getQualifiedGoal(), mojo.getGoal(), mojo.getDisplayName()));
        }
      }

      sort(myGoalNodes);
      updateFrom(this);
      childrenChanged();
    }

    @Override
    public boolean isVisible() {
      // show regardless absence of children
      return super.isVisible() || getDisplayKind() != DisplayKind.NEVER;
    }
  }

  public class PluginGoalNode extends GoalNode {

    private final String myUnqualifiedGoal;

    public PluginGoalNode(PluginNode parent, String goal, String unqualifiedGoal, String displayName) {
      super(parent, goal, displayName);
      getTemplatePresentation().setIcon(MavenIcons.PluginGoal);
      myUnqualifiedGoal = unqualifiedGoal;
    }

    @Nullable
    @Override
    public Navigatable getNavigatable() {
      PluginNode pluginNode = (PluginNode)getParent();

      MavenDomPluginModel pluginModel = MavenPluginDomUtil.getMavenPluginModel(myProject,
                                                                               pluginNode.getPlugin().getGroupId(),
                                                                               pluginNode.getPlugin().getArtifactId(),
                                                                               pluginNode.getPlugin().getVersion());

      if (pluginModel == null) return null;

      for (MavenDomMojo mojo : pluginModel.getMojos().getMojos()) {
        final XmlElement xmlElement = mojo.getGoal().getXmlElement();

        if (xmlElement instanceof Navigatable && Objects.equals(myUnqualifiedGoal, mojo.getGoal().getStringValue())) {
          return new NavigatableAdapter() {
            @Override
            public void navigate(boolean requestFocus) {
              ((Navigatable)xmlElement).navigate(requestFocus);
            }
          };
        }
      }

      return null;
    }
  }

  public abstract class BaseDependenciesNode extends GroupNode {
    protected final MavenProject myMavenProject;
    private List<DependencyNode> myChildren = new ArrayList<>();

    protected BaseDependenciesNode(MavenSimpleNode parent, MavenProject mavenProject) {
      super(parent);
      myMavenProject = mavenProject;
    }

    public MavenProject getMavenProject() {
      return myMavenProject;
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
      return myChildren;
    }

    protected void updateChildren(List<MavenArtifactNode> children, MavenProject mavenProject) {
      List<DependencyNode> newNodes = null;
      int validChildCount = 0;

      for (MavenArtifactNode each : children) {
        if (each.getState() != MavenArtifactState.ADDED &&
            each.getState() != MavenArtifactState.CONFLICT &&
            each.getState() != MavenArtifactState.DUPLICATE) {
          continue;
        }

        if (newNodes == null) {
          if (validChildCount < myChildren.size()) {
            DependencyNode currentValidNode = myChildren.get(validChildCount);

            if (currentValidNode.myArtifact.equals(each.getArtifact())
                && currentValidNode.myArtifactNode.getArtifact().isResolvedArtifact() == each.getArtifact().isResolvedArtifact()) {
              if (each.getState() == MavenArtifactState.ADDED) {
                currentValidNode.updateChildren(each.getDependencies(), mavenProject);
              }
              currentValidNode.updateDependency();

              validChildCount++;
              continue;
            }
          }

          newNodes = new ArrayList<>(children.size());
          newNodes.addAll(myChildren.subList(0, validChildCount));
        }

        DependencyNode newNode = findOrCreateNodeFor(each, mavenProject, validChildCount);
        newNodes.add(newNode);
        if (each.getState() == MavenArtifactState.ADDED) {
          newNode.updateChildren(each.getDependencies(), mavenProject);
        }
        newNode.updateDependency();
      }

      if (newNodes == null) {
        if (validChildCount == myChildren.size()) {
          return; // All nodes are valid, child did not changed.
        }

        assert validChildCount < myChildren.size();

        newNodes = new ArrayList<>(myChildren.subList(0, validChildCount));
      }

      myChildren = newNodes;
      childrenChanged();
    }

    private DependencyNode findOrCreateNodeFor(MavenArtifactNode artifact, MavenProject mavenProject, int from) {
      for (int i = from; i < myChildren.size(); i++) {
        DependencyNode node = myChildren.get(i);
        if (node.myArtifact.equals(artifact.getArtifact())
            && node.myArtifactNode.getArtifact().isResolvedArtifact() == artifact.getArtifact().isResolvedArtifact()) {
          return node;
        }
      }
      return new DependencyNode(this, artifact, mavenProject);
    }

    @Override
    String getMenuId() {
      return "Maven.DependencyMenu";
    }
  }

  public class DependenciesNode extends BaseDependenciesNode {
    public DependenciesNode(ProjectNode parent, MavenProject mavenProject) {
      super(parent, mavenProject);
      getTemplatePresentation().setIcon(AllIcons.Nodes.PpLibFolder);
    }

    @Override
    public String getName() {
      return message("view.node.dependencies");
    }

    public void updateDependencies() {
      updateChildren(myMavenProject.getDependencyTree(), myMavenProject);
    }
  }

  public class DependencyNode extends BaseDependenciesNode {
    private final MavenArtifact myArtifact;
    private final MavenArtifactNode myArtifactNode;

    public DependencyNode(MavenSimpleNode parent, MavenArtifactNode artifactNode, MavenProject mavenProject) {
      super(parent, mavenProject);
      myArtifactNode = artifactNode;
      myArtifact = artifactNode.getArtifact();
      setUniformIcon(AllIcons.Nodes.PpLib);
    }

    public MavenArtifact getArtifact() {
      return myArtifact;
    }

    @Override
    public String getName() {
      return myArtifact.getDisplayStringForLibraryName();
    }

    private String getToolTip() {
      final StringBuilder myToolTip = new StringBuilder();
      String scope = myArtifactNode.getOriginalScope();

      if (StringUtil.isNotEmpty(scope) && !MavenConstants.SCOPE_COMPILE.equals(scope)) {
        myToolTip.append(scope).append(" ");
      }
      if (myArtifactNode.getState() == MavenArtifactState.CONFLICT) {
        myToolTip.append("omitted for conflict");
        if (myArtifactNode.getRelatedArtifact() != null) {
          myToolTip.append(" with ").append(myArtifactNode.getRelatedArtifact().getVersion());
        }
      }
      if (myArtifactNode.getState() == MavenArtifactState.DUPLICATE) {
        myToolTip.append("omitted for duplicate");
      }
      return myToolTip.toString().trim();
    }

    @Override
    protected void doUpdate() {
      setNameAndTooltip(getName(), null, getToolTip());
    }

    @Override
    protected void setNameAndTooltip(String name, @Nullable String tooltip, SimpleTextAttributes attributes) {
      final SimpleTextAttributes mergedAttributes;
      if (myArtifactNode.getState() == MavenArtifactState.CONFLICT || myArtifactNode.getState() == MavenArtifactState.DUPLICATE) {
        mergedAttributes = SimpleTextAttributes.merge(attributes, SimpleTextAttributes.GRAYED_ATTRIBUTES);
      } else {
        mergedAttributes = attributes;
      }
      super.setNameAndTooltip(name, tooltip, mergedAttributes);
    }

    private void updateDependency() {
      setErrorLevel(MavenArtifactUtilKt.resolved(myArtifact) ? ErrorLevel.NONE : ErrorLevel.ERROR);
    }

    @Override
    public Navigatable getNavigatable() {
      final MavenArtifactNode parent = myArtifactNode.getParent();
      final VirtualFile file;
      if (parent == null) {
        file = getMavenProject().getFile();
      }
      else {
        final MavenId id = parent.getArtifact().getMavenId();
        final MavenProject pr = myProjectsManager.findProject(id);
        file = pr == null ? MavenNavigationUtil.getArtifactFile(getProject(), id) : pr.getFile();
      }
      return file == null ? null : MavenNavigationUtil.createNavigatableForDependency(getProject(), file, getArtifact());
    }

    @Override
    public boolean isVisible() {
      // show regardless absence of children
      return getDisplayKind() != DisplayKind.NEVER;
    }
  }

  public class RunConfigurationsNode extends GroupNode {

    private final List<RunConfigurationNode> myChildren = new ArrayList<>();

    public RunConfigurationsNode(ProjectNode parent) {
      super(parent);
      getTemplatePresentation().setIcon(Task);
    }

    @Override
    public String getName() {
      return message("view.node.run.configurations");
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
      return myChildren;
    }

    public void updateRunConfigurations(MavenProject mavenProject) {
      boolean childChanged = false;

      Set<RunnerAndConfigurationSettings> settings = new HashSet<>(
        RunManager.getInstance(myProject).getConfigurationSettingsList(MavenRunConfigurationType.getInstance()));

      for (Iterator<RunConfigurationNode> itr = myChildren.iterator(); itr.hasNext(); ) {
        RunConfigurationNode node = itr.next();

        if (settings.remove(node.getSettings())) {
          node.updateRunConfiguration();
        }
        else {
          itr.remove();
          childChanged = true;
        }
      }

      String directory = mavenProject.getDirectory();

      int oldSize = myChildren.size();

      for (RunnerAndConfigurationSettings cfg : settings) {
        MavenRunConfiguration mavenRunConfiguration = (MavenRunConfiguration)cfg.getConfiguration();

        if (VfsUtilCore.pathEqualsTo(mavenProject.getDirectoryFile(), mavenRunConfiguration.getRunnerParameters().getWorkingDirPath())) {
          myChildren.add(new RunConfigurationNode(this, cfg));
        }
      }

      if (oldSize != myChildren.size()) {
        childChanged = true;
        sort(myChildren);
      }

      if (childChanged) {
        childrenChanged();
      }
    }
  }

  public class RunConfigurationNode extends MavenSimpleNode {
    private final RunnerAndConfigurationSettings mySettings;

    public RunConfigurationNode(RunConfigurationsNode parent, RunnerAndConfigurationSettings settings) {
      super(parent);
      mySettings = settings;
      getTemplatePresentation().setIcon(ProgramRunnerUtil.getConfigurationIcon(settings, false));
    }

    public RunnerAndConfigurationSettings getSettings() {
      return mySettings;
    }

    @Override
    public String getName() {
      return mySettings.getName();
    }

    @Override
    protected void doUpdate() {
      setNameAndTooltip(getName(),
                        null,
                        StringUtil.join(((MavenRunConfiguration)mySettings.getConfiguration()).getRunnerParameters().getGoals(), " "));
    }

    @Nullable
    @Override
    String getMenuId() {
      return "Maven.RunConfigurationMenu";
    }

    public void updateRunConfiguration() {

    }

    @Override
    public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
      MavenActionsUsagesCollector
        .trigger(myProject, MavenActionsUsagesCollector.EXECUTE_MAVEN_CONFIGURATION, TOOL_WINDOW_PLACE_ID, false, null);
      ProgramRunnerUtil.executeConfiguration(mySettings, DefaultRunExecutor.getRunExecutorInstance());
    }
  }

  private class UpdatePluginsTreeTask implements Runnable {
    @NotNull private final PluginsNode myParentNode;
    private final List<MavenPlugin> myPlugins;

    UpdatePluginsTreeTask(PluginsNode parentNode, List<MavenPlugin> plugins) {
      myParentNode = parentNode;
      myPlugins = plugins;
    }


    @Override
    public void run() {
      File localRepository = myProjectsManager.getLocalRepository();

      List<PluginNode> pluginInfos = new ArrayList<>();
      Iterator<MavenPlugin> iterator = myPlugins.iterator();
      while(!isUnloading && iterator.hasNext()){
        MavenPlugin next = iterator.next();
        pluginInfos.add(new PluginNode(myParentNode, next, MavenArtifactUtil
          .readPluginInfo(localRepository, next.getMavenId())));
      }
      updateNodesInEDT(pluginInfos);
    }

    private void updateNodesInEDT(List<PluginNode> pluginNodes) {
      ApplicationManager.getApplication().invokeLater(() -> {
        myParentNode.myPluginNodes.clear();
        if(isUnloading) return;
        myParentNode.myPluginNodes.addAll(pluginNodes);
        myParentNode.sort(myParentNode.myPluginNodes);
        myParentNode.childrenChanged();
      });
    }
  }
}