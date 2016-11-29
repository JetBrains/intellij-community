/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.psi.xml.XmlElement;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.*;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
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
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.*;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.net.URL;
import java.util.*;
import java.util.List;

import static org.jetbrains.idea.maven.project.ProjectBundle.message;

public class MavenProjectsStructure extends SimpleTreeStructure {
  private static final URL ERROR_ICON_URL = MavenProjectsStructure.class.getResource("/general/error.png");
  private static final Collection<String> BASIC_PHASES = MavenConstants.BASIC_PHASES;
  private static final Collection<String> PHASES = MavenConstants.PHASES;

  private static final Comparator<MavenSimpleNode> NODE_COMPARATOR = (o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true);

  private final Project myProject;
  private final MavenProjectsManager myProjectsManager;
  private final MavenTasksManager myTasksManager;
  private final MavenShortcutsManager myShortcutsManager;
  private final MavenProjectsNavigator myProjectsNavigator;

  private final SimpleTreeBuilder myTreeBuilder;
  private final RootNode myRoot = new RootNode();

  private final Map<MavenProject, ProjectNode> myProjectToNodeMapping = new THashMap<>();

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

    configureTree(tree);

    myTreeBuilder = new SimpleTreeBuilder(tree, (DefaultTreeModel)tree.getModel(), this, null);
    Disposer.register(myProject, myTreeBuilder);

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
    myTreeBuilder.addSubtreeToUpdateByElement(node);
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
          newParentNode = aggregatorNode.getModulesNode();
        }
      }
    }

    node.updateProject();
    reconnectNode(node, newParentNode);

    ProjectsGroupNode newModulesParentNode = myProjectsNavigator.getGroupModules() && node.isVisible() ? node.getModulesNode() : myRoot;
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
      return result.toArray(new MavenSimpleNode[result.size()]);
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

    protected void setNameAndTooltip(String name, @Nullable String tooltip) {
      setNameAndTooltip(name, tooltip, (String)null);
    }

    protected void setNameAndTooltip(String name, @Nullable String tooltip, @Nullable String hint) {
      setNameAndTooltip(name, tooltip, getPlainAttributes());
      if (showDescriptions() && !StringUtil.isEmptyOrSpaces(hint)) {
        addColoredFragment(" (" + hint + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }

    protected void setNameAndTooltip(String name, @Nullable String tooltip, SimpleTextAttributes attributes) {
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
        MavenUIUtil.executeAction(actionId, inputEvent);
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
      Collections.sort(list, NODE_COMPARATOR);
    }
  }

  public abstract class ProjectsGroupNode extends GroupNode {
    private final List<ProjectNode> myProjectNodes = new ArrayList<>();

    public ProjectsGroupNode(MavenSimpleNode parent) {
      super(parent);
      setUniformIcon(MavenIcons.ModulesClosed);
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
      final List<MavenDomProfile> profiles = ContainerUtil.newArrayList();

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
            final JBList list = new JBList(profiles);
            list.setCellRenderer(new DefaultListCellRenderer() {
              @Override
              public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                @SuppressWarnings("unchecked") MavenDomProfile mavenDomProfile = (MavenDomProfile)value;
                XmlElement xmlElement = mavenDomProfile.getXmlElement();
                if (xmlElement != null) {
                  setText(xmlElement.getContainingFile().getVirtualFile().getPath());
                }
                return result;
              }
            });
            JBPopupFactory.getInstance().createListPopupBuilder(list)
              .setTitle("Choose file to open ")
              .setItemChoosenCallback(() -> {
                final Object value = list.getSelectedValue();
                if (value instanceof MavenDomProfile) {
                  final Navigatable navigatable = getNavigatable((MavenDomProfile)value);
                  if (navigatable != null) navigatable.navigate(requestFocus);
                }
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

  public class ProjectNode extends GroupNode {
    private final MavenProject myMavenProject;
    private final LifecycleNode myLifecycleNode;
    private final PluginsNode myPluginsNode;
    private final DependenciesNode myDependenciesNode;
    private final ModulesNode myModulesNode;
    private final RunConfigurationsNode myRunConfigurationsNode;

    private String myTooltipCache;

    public ProjectNode(@NotNull MavenProject mavenProject) {
      super(null);
      myMavenProject = mavenProject;

      myLifecycleNode = new LifecycleNode(this);
      myPluginsNode = new PluginsNode(this);
      myDependenciesNode = new DependenciesNode(this, mavenProject);
      myModulesNode = new ModulesNode(this);
      myRunConfigurationsNode = new RunConfigurationsNode(this);

      setUniformIcon(MavenIcons.MavenProject);
      updateProject();
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
      return Arrays.asList(myLifecycleNode, myPluginsNode, myRunConfigurationsNode, myDependenciesNode, myModulesNode);
    }

    public ModulesNode getModulesNode() {
      return myModulesNode;
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

    private String makeDescription() {
      StringBuilder desc = new StringBuilder();
      desc.append("<html>" +
                  "<table>" +
                  "<tr>" +
                  "<td nowrap>" +
                  "<table>" +
                  "<tr>" +
                  "<td nowrap>Project:</td>" +
                  "<td nowrap>").append(myMavenProject.getMavenId())
        .append("</td>" +
                "</tr>" +
                "<tr>" +
                "<td nowrap>Location:</td>" +
                "<td nowrap>").append(myMavenProject.getPath())
        .append("</td>" +
                "</tr>" +
                "</table>" +
                "</td>" +
                "</tr>");

      appendProblems(desc);

      desc.append("</table></html>");

      return desc.toString();
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
          desc.append("<td nowrap valign=top>Problems:</td>");
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
      String text = StringUtil.replace(each.getDescription(), new String[]{"<", ">"}, new String[]{"&lt;", "&gt;"});
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
      setUniformIcon(MavenIcons.ModulesClosed);
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
      setUniformIcon(MavenIcons.Phase);
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
      setUniformIcon(MavenIcons.PhasesClosed);
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
      setUniformIcon(MavenIcons.PhasesClosed);
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

      for (Iterator<PluginNode> itr = myPluginNodes.iterator(); itr.hasNext(); ) {
        PluginNode each = itr.next();

        if (plugins.contains(each.getPlugin())) {
          each.updatePlugin();
        }
        else {
          itr.remove();
        }
      }
      for (MavenPlugin each : plugins) {
        if (!hasNodeFor(each)) {
          myPluginNodes.add(new PluginNode(this, each));
        }
      }

      sort(myPluginNodes);
      childrenChanged();
    }

    private boolean hasNodeFor(MavenPlugin plugin) {
      for (PluginNode each : myPluginNodes) {
        if (each.getPlugin().getMavenId().equals(plugin.getMavenId())) {
          return true;
        }
      }
      return false;
    }
  }

  public class PluginNode extends GoalsGroupNode {
    private final MavenPlugin myPlugin;
    private MavenPluginInfo myPluginInfo;

    public PluginNode(PluginsNode parent, MavenPlugin plugin) {
      super(parent);
      myPlugin = plugin;

      setUniformIcon(MavenIcons.MavenPlugin);
      updatePlugin();
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

    public void updatePlugin() {
      boolean hadPluginInfo = myPluginInfo != null;

      myPluginInfo = MavenArtifactUtil.readPluginInfo(myProjectsManager.getLocalRepository(), myPlugin.getMavenId());

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
      setUniformIcon(MavenIcons.PluginGoal);
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

        if (xmlElement instanceof Navigatable && Comparing.equal(myUnqualifiedGoal, mojo.getGoal().getStringValue())) {
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

            if (currentValidNode.myArtifact.equals(each.getArtifact())) {
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
        if (node.myArtifact.equals(artifact.getArtifact())) {
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
      setUniformIcon(AllIcons.Nodes.PpLibFolder);
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
      final StringBuilder myToolTip = new StringBuilder("");
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
      setErrorLevel(myArtifact.isResolved() ? ErrorLevel.NONE : ErrorLevel.ERROR);
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
      setUniformIcon(MavenIcons.Phase);
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

      Set<RunnerAndConfigurationSettings> settings = new THashSet<>(
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

      String directory = PathUtil.getCanonicalPath(mavenProject.getDirectory());

      int oldSize = myChildren.size();

      for (RunnerAndConfigurationSettings cfg : settings) {
        MavenRunConfiguration mavenRunConfiguration = (MavenRunConfiguration)cfg.getConfiguration();

        if (directory.equals(PathUtil.getCanonicalPath(mavenRunConfiguration.getRunnerParameters().getWorkingDirPath()))) {
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
      setUniformIcon(ProgramRunnerUtil.getConfigurationIcon(settings, false));
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
      ProgramRunnerUtil.executeConfiguration(myProject, mySettings, DefaultRunExecutor.getRunExecutorInstance());
    }
  }
}