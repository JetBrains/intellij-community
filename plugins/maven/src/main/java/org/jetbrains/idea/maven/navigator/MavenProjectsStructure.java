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

import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.*;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.*;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;

import static org.jetbrains.idea.maven.project.ProjectBundle.message;

public class MavenProjectsStructure extends SimpleTreeStructure {
  private static final URL ERROR_ICON_URL = MavenProjectsStructure.class.getResource("/images/error.png");
  private static final Collection<String> BASIC_PHASES = MavenEmbedderWrapper.BASIC_PHASES;
  private static final Collection<String> PHASES = MavenEmbedderWrapper.PHASES;

  private static final Comparator<MavenSimpleNode> NODE_COMPARATOR = new Comparator<MavenSimpleNode>() {
    public int compare(MavenSimpleNode o1, MavenSimpleNode o2) {
      return Comparing.compare(o1.getName(), o2.getName());
    }
  };

  private final Project myProject;
  private final MavenProjectsManager myProjectsManager;
  private final MavenTasksManager myTasksManager;
  private final MavenShortcutsManager myShortcutsManager;
  private final MavenProjectsNavigator myProjectsNavigator;

  private final SimpleTreeBuilder myTreeBuilder;
  private final RootNode myRoot = new RootNode();

  private final Map<MavenProject, ProjectNode> myProjectToNodeMapping = new THashMap<MavenProject, ProjectNode>();

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
      public void toggle(TreePath treePath, InputEvent e) {
        SimpleNode node = tree.getNodeFor(treePath);
        if (node != null) {
          node.handleDoubleClickOrEnter(tree, e);
        }
      }

      public boolean isVisible(Object userObject) {
        return userObject instanceof ProfileNode;
      }

      public MavenUIUtil.CheckBoxState getState(Object userObject) {
        MavenProfileState state = ((ProfileNode)userObject).getState();
        switch (state) {
          case NONE: return MavenUIUtil.CheckBoxState.UNCHECKED;
          case EXPLICIT:  return MavenUIUtil.CheckBoxState.CHECKED;
          case IMPLICIT: return MavenUIUtil.CheckBoxState.PARTIAL;
        }
        MavenLog.LOG.error("unknown profile state: " + state);
        return MavenUIUtil.CheckBoxState.UNCHECKED;
      }
    });
  }

  public RootNode getRootElement() {
    return myRoot;
  }

  public void update() {
    List<MavenProject> projects = myProjectsManager.getProjects();
    List<MavenProject> deleted = new ArrayList<MavenProject>(myProjectToNodeMapping.keySet());
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

  public void updateProjects(List<MavenProject> updated, List<MavenProject> deleted) {
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
    final List<T> filtered = new ArrayList<T>();
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
    List<SimpleNode> nodes = new ArrayList<SimpleNode>();
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

      List<MavenSimpleNode> result = new ArrayList<MavenSimpleNode>();
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

    protected void setNameAndTooltip(String name, @Nullable String tooltip, SimpleTextAttributes attribs) {
      clearColoredText();
      addColoredFragment(name, prepareAttribs(attribs));
      getTemplatePresentation().setTooltip(tooltip);
    }

    private SimpleTextAttributes prepareAttribs(SimpleTextAttributes from) {
      ErrorLevel level = getTotalErrorLevel();
      Color waveColor = level == ErrorLevel.NONE ? null : Color.RED;
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
      final VirtualFile file = getVirtualFile();
      if (file == null || !file.isValid()) return null;
      final PsiFile result = PsiManager.getInstance(getProject()).findFile(file);
      return result == null ? null : new Navigatable.Adapter() {
        public void navigate(boolean requestFocus) {
          int offset = 0;
          if (result instanceof XmlFile) {
            final XmlDocument xml = ((XmlFile)result).getDocument();
            if (xml != null) {
              final XmlTag rootTag = xml.getRootTag();
              if (rootTag != null) {
                final XmlTag[] id = rootTag.findSubTags("artifactId", rootTag.getNamespace());
                if (id.length > 0) {
                  offset = id[0].getValue().getTextRange().getStartOffset();
                }
              }
            }
          }
          new OpenFileDescriptor(getProject(), file, offset).navigate(requestFocus);
        }
      };
    }

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
    private final List<ProjectNode> myProjectNodes = new ArrayList<ProjectNode>();

    public ProjectsGroupNode(MavenSimpleNode parent) {
      super(parent);
      setIcons(MavenIcons.CLOSED_MODULES_ICON, MavenIcons.OPEN_MODULES_ICON);
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
    private List<ProfileNode> myProfileNodes = new ArrayList<ProfileNode>();

    public ProfilesNode(MavenSimpleNode parent) {
      super(parent);
      setIcons(MavenIcons.CLOSED_PROFILES_ICON, MavenIcons.OPEN_PROFILES_ICON);
    }

    protected List<? extends MavenSimpleNode> doGetChildren() {
      return myProfileNodes;
    }

    @Override
    public String getName() {
      return message("view.node.profiles");
    }

    public void updateProfiles() {
      Collection<Pair<String,MavenProfileState>> profiles = myProjectsManager.getProfilesWithStates();

      List<ProfileNode> newNodes = new ArrayList<ProfileNode>(profiles.size());
      for (Pair<String, MavenProfileState> each : profiles) {
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
    private MavenProfileState myState;

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

    public MavenProfileState getState() {
      return myState;
    }

    private void setState(MavenProfileState state) {
      myState = state;
    }

    @Nullable
    @NonNls
    protected String getActionId() {
      return "Maven.ToggleProfile";
    }
  }

  public class ProjectNode extends GroupNode {
    private final MavenProject myMavenProject;
    private final LifecycleNode myLifecycleNode;
    private final PluginsNode myPluginsNode;
    private final DependenciesNode myDependenciesNode;
    private final ModulesNode myModulesNode;

    private String myTooltipCache;

    public ProjectNode(MavenProject mavenProject) {
      super(null);
      myMavenProject = mavenProject;

      myLifecycleNode = new LifecycleNode(this);
      myPluginsNode = new PluginsNode(this);
      myDependenciesNode = new DependenciesNode(this, mavenProject);
      myModulesNode = new ModulesNode(this);

      setUniformIcon(MavenIcons.MAVEN_PROJECT_ICON);
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
      return Arrays.asList(myLifecycleNode, myPluginsNode, myDependenciesNode, myModulesNode);
    }


    public ModulesNode getModulesNode() {
      return myModulesNode;
    }

    private void updateProject() {
      setErrorLevel(myMavenProject.getProblems().isEmpty() ? ErrorLevel.NONE : ErrorLevel.ERROR);
      myLifecycleNode.updateGoalsList();
      myPluginsNode.updatePlugins(myMavenProject);
      myDependenciesNode.updateDependencies();

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

    @Override
    public String getName() {
      return myMavenProject.getDisplayName();
    }

    @Override
    protected void doUpdate() {
      setNameAndTooltip(getName(), myTooltipCache);
    }

    @Override
    protected SimpleTextAttributes getPlainAttributes() {
      if (myProjectsManager.isIgnored(myMavenProject)) {
        return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.GRAY);
      }
      return super.getPlainAttributes();
    }

    private String makeDescription() {
      StringBuilder desc = new StringBuilder();
      desc.append("<html>");
      desc.append("<table>");
      desc.append("<tr>");
      desc.append("<td>");
      desc.append("  <table>");
      desc.append("  <tr>");
      desc.append("  <td>Project:</td>");
      desc.append("  <td nowrap>" + myMavenProject.getMavenId() + "</td>");
      desc.append("  </tr>");
      desc.append("  <tr>");
      desc.append("  <td>Location:</td>");
      desc.append("  <td nowrap>" + myMavenProject.getPath() + "</td>");
      desc.append("  </tr>");
      desc.append("  </table>");
      desc.append("</td>");
      desc.append("</tr>");
      appendProblems(desc);

      desc.append("</table>");
      desc.append("</html>");
      return desc.toString();
    }

    private void appendProblems(StringBuilder desc) {
      List<MavenProjectProblem> problems = myMavenProject.getProblems();
      if (problems.isEmpty()) return;

      desc.append("<tr>");
      desc.append("<td>");
      desc.append("<table>");
      boolean first = true;
      for (MavenProjectProblem each : problems) {
        desc.append("<tr>");
        if (first) {
          desc.append("<td valign=top>" + MavenUtil.formatHtmlImage(ERROR_ICON_URL) + "</td>");
          desc.append("<td valign=top>Problems:</td>");
          first = false;
        }
        else {
          desc.append("<td colspan=2></td>");
        }
        desc.append("<td valign=top>" + wrappedText(each));
        desc.append("</tr>");
      }
      desc.append("</table>");
      desc.append("</td>");
      desc.append("</tr>");
    }

    private String wrappedText(MavenProjectProblem each) {
      String text = StringUtil.replace(each.getDescription(), new String[]{"<", ">"}, new String[]{"&lt;", "&gt;"});
      StringBuffer result = new StringBuffer();
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

    @Nullable
    @NonNls
    protected String getMenuId() {
      return "Maven.NavigatorProjectMenu";
    }
  }

  public class ModulesNode extends ProjectsGroupNode {
    public ModulesNode(ProjectNode parent) {
      super(parent);
      setIcons(MavenIcons.CLOSED_MODULES_ICON, MavenIcons.OPEN_MODULES_ICON);
    }

    @Override
    public String getName() {
      return message("view.node.modules");
    }
  }

  public abstract class GoalsGroupNode extends GroupNode {
    protected final List<GoalNode> myGoalNodes = new ArrayList<GoalNode>();

    public GoalsGroupNode(MavenSimpleNode parent) {
      super(parent);
    }

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
      setUniformIcon(MavenIcons.PHASE_ICON);
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
      String hint = StringUtil.join(Arrays.asList(myShortcutsManager.getDescription(myMavenProject, myGoal),
                                                  myTasksManager.getDescription(myMavenProject, myGoal)), ", ");
      setNameAndTooltip(getName(), null, hint);
    }

    @Override
    protected SimpleTextAttributes getPlainAttributes() {
      if (myGoal.equals(myMavenProject.getDefaultGoal())) {
        return new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, getColor());
      }
      return super.getPlainAttributes();
    }

    @Nullable
    @NonNls
    protected String getActionId() {
      return "Maven.RunBuild";
    }

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
      setIcons(MavenIcons.CLOSED_PHASES_ICON, MavenIcons.OPEN_PHASES_ICON);
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
    private final List<PluginNode> myPluginNodes = new ArrayList<PluginNode>();

    public PluginsNode(ProjectNode parent) {
      super(parent);
      setIcons(MavenIcons.CLOSED_PLUGINS_ICON, MavenIcons.OPEN_PLUGINS_ICON);
    }

    @Override
    public String getName() {
      return message("view.node.plugins");
    }

    protected List<? extends MavenSimpleNode> doGetChildren() {
      return myPluginNodes;
    }

    public void updatePlugins(MavenProject mavenProject) {
      List<MavenPlugin> plugins = mavenProject.getDeclaredPlugins();

      for (PluginNode each : new ArrayList<PluginNode>(myPluginNodes)) {
        if (plugins.contains(each.getPlugin())) {
          each.updatePlugin();
        }
        else {
          myPluginNodes.remove(each);
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

      setUniformIcon(MavenIcons.PLUGIN_ICON);
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
          myGoalNodes.add(new PluginGoalNode(this, mojo.getQualifiedGoal(), mojo.getDisplayName()));
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
    public PluginGoalNode(PluginNode parent, String goal, String displayName) {
      super(parent, goal, displayName);
      setUniformIcon(MavenIcons.PLUGIN_GOAL_ICON);
    }
  }

  public abstract class BaseDependenciesNode extends GroupNode {
    protected final MavenProject myMavenProject;
    private List<DependencyNode> myChildren = new ArrayList<DependencyNode>();

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
      List<DependencyNode> newNodes = new ArrayList<DependencyNode>(children.size());
      for (MavenArtifactNode each : children) {
        if (each.getState() != MavenArtifactState.ADDED) continue;
        
        DependencyNode newNode = findOrCreateNodeFor(each, mavenProject);
        newNodes.add(newNode);
        newNode.updateChildren(each.getDependencies(), mavenProject);
        newNode.updateDependency();
      }
      myChildren = newNodes;
      childrenChanged();
    }

    private DependencyNode findOrCreateNodeFor(MavenArtifactNode artifact, MavenProject mavenProject) {
      for (DependencyNode each : myChildren) {
        if (each.myArtifact.equals(artifact.getArtifact())) return each;
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
      setIcons(MavenIcons.CLOSED_DEPENDENCIES_ICON, MavenIcons.OPEN_DEPENDENCIES_ICON);
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
      setUniformIcon(MavenIcons.DEPENDENCY_ICON);
    }

    public MavenArtifact getArtifact() {
      return myArtifact;
    }

    @Override
    public String getName() {
      return myArtifact.getDisplayStringForLibraryName();
    }

    @Override
    protected void doUpdate() {
      String scope = myArtifact.getScope();
      setNameAndTooltip(getName(), null, MavenConstants.SCOPE_COMPILE.equals(scope) ? null : scope);
    }

    private void updateDependency() {
      setErrorLevel(myArtifact.isResolved() ? ErrorLevel.NONE : ErrorLevel.ERROR);
    }

    @Override
    public Navigatable getNavigatable() {
      final Module m = myProjectsManager.findModule(myMavenProject);
      if (m == null) return null;
      final File pom = MavenArtifactUtil.getArtifactFile(myProjectsManager.getLocalRepository(), myArtifact.getMavenId());
      final VirtualFile vPom;
      if (pom.exists()) {
       vPom = LocalFileSystem.getInstance().findFileByIoFile(pom);
      } else {
        final MavenProject mavenProject = myProjectsManager.findProject(myArtifact);
        vPom = mavenProject == null ? null : mavenProject.getFile();
      }
      if (vPom != null) {
        return new Navigatable.Adapter() {
          public void navigate(boolean requestFocus) {
            int offset = 0;
            try {
              int index = new String(vPom.contentsToByteArray()).indexOf("<artifactId>" + myArtifact.getArtifactId() + "</artifactId>");
              if (index != -1) {
                offset += index + 12;
              }
            }
            catch (IOException e) {//
            }
            new OpenFileDescriptor(myProject, vPom, offset).navigate(requestFocus);
          }
        };
      }
      final OrderEntry e = MavenRootModelAdapter.findLibraryEntry(m, myArtifact);
      if (e == null) return null;
      return new Navigatable.Adapter() {
        public void navigate(boolean requestFocus) {
          ProjectSettingsService.getInstance(myProject).openProjectLibrarySettings(new NamedLibraryElement(m, e));
        }
      };
    }

    @Override
    public boolean isVisible() {
      // show regardless absence of children
      return getDisplayKind() != DisplayKind.NEVER;
    }
  }
}
