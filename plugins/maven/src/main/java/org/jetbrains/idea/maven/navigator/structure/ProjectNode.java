// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import icons.MavenIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenProjectProblem;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.intellij.openapi.ui.UiUtils.getPresentablePath;
import static org.jetbrains.idea.maven.project.MavenProjectBundle.message;

class ProjectNode extends ProjectsGroupNode implements MavenProjectNode {
  private static final URL ERROR_ICON_URL = MavenProjectsStructure.class.getResource("/general/error.png");

  private final MavenProject myMavenProject;
  private final LifecycleNode myLifecycleNode;
  private final PluginsNode myPluginsNode;
  private final DependenciesNode myDependenciesNode;
  private final RunConfigurationsNode myRunConfigurationsNode;

  private @NlsContexts.Tooltip String myTooltipCache;

  ProjectNode(MavenProjectsStructure structure, @NotNull MavenProject mavenProject) {
    super(structure, null);
    myMavenProject = mavenProject;

    myLifecycleNode = new LifecycleNode(structure, this);
    myPluginsNode = new PluginsNode(structure, this);
    myDependenciesNode = new DependenciesNode(structure, this, mavenProject);
    myRunConfigurationsNode = new RunConfigurationsNode(structure, this);

    getTemplatePresentation().setIcon(MavenIcons.MavenProject);
  }

  @Override
  public MavenNodeType getType() {
    return MavenNodeType.PROJECT;
  }

  @Override
  public MavenProject getMavenProject() {
    return myMavenProject;
  }

  public ProjectsGroupNode getGroup() {
    return (ProjectsGroupNode)super.getParent();
  }

  @Override
  public boolean isVisible() {
    if (!getProjectsNavigator().getShowIgnored() && getProjectsManager().isIgnored(myMavenProject)) return false;
    return super.isVisible();
  }

  private MavenProjectsManager getProjectsManager() {
    return myMavenProjectsStructure.getProjectsManager();
  }

  private MavenProjectsNavigator getProjectsNavigator() {
    return myMavenProjectsStructure.getProjectsNavigator();
  }

  @Override
  protected List<? extends MavenSimpleNode> doGetChildren() {
    var children =
      new CopyOnWriteArrayList<MavenSimpleNode>(List.of(myLifecycleNode, myPluginsNode, myRunConfigurationsNode, myDependenciesNode));
    children.addAll(super.doGetChildren());
    return children;
  }

  void updateProject() {
    setErrorLevel(myMavenProject.getCacheProblems().isEmpty() ? MavenProjectsStructure.ErrorLevel.NONE : MavenProjectsStructure.ErrorLevel.ERROR);
    myLifecycleNode.updateGoalsList();
    myPluginsNode.updatePlugins(myMavenProject);

    if (myMavenProjectsStructure.getDisplayMode() == MavenProjectsStructure.MavenStructureDisplayMode.SHOW_ALL) {
      myDependenciesNode.updateDependencies();
    }

    myRunConfigurationsNode.updateRunConfigurations(myMavenProject);

    myTooltipCache = makeDescription();

    myMavenProjectsStructure.updateFrom(getParent());
  }

  public void updateIgnored() {
    getGroup().childrenChanged();
  }

  public void updateGoals() {
    myMavenProjectsStructure.updateFrom(myLifecycleNode);
    myMavenProjectsStructure.updateFrom(myPluginsNode);
  }

  public void updateRunConfigurations() {
    myRunConfigurationsNode.updateRunConfigurations(myMavenProject);
    myMavenProjectsStructure.updateFrom(myRunConfigurationsNode);
  }

  @Override
  public String getName() {
    if (getProjectsNavigator().getAlwaysShowArtifactId()) {
      return myMavenProject.getMavenId().getArtifactId();
    }
    else {
      return myMavenProject.getDisplayName();
    }
  }

  @Override
  protected void doUpdate(@NotNull PresentationData presentation) {
    String hint = null;

    if (!getProjectsNavigator().getGroupModules()
        && getProjectsManager().findAggregator(myMavenProject) == null
        && getProjectsManager().getProjects().size() > getProjectsManager().getRootProjects().size()) {
      hint = "root";
    }

    setNameAndTooltip(presentation, getName(), myTooltipCache, hint);
  }

  @Override
  protected SimpleTextAttributes getPlainAttributes() {
    if (getProjectsManager().isIgnored(myMavenProject)) {
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
      .append("<td nowrap>").append(getPresentablePath(myMavenProject.getPath())).append("</td>")
      .append("</tr>")
      .append("</table>").append("</td>")
      .append("</tr>");

    appendProblems(desc);

    desc.append("</table>")
      .append("</html>");

    return desc.toString(); //NON-NLS
  }

  private void appendProblems(StringBuilder desc) {
    List<MavenProjectProblem> problems = myMavenProject.getCacheProblems();
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
  protected void setNameAndTooltip(@NotNull PresentationData presentation,
                                   String name,
                                   @Nullable String tooltip,
                                   SimpleTextAttributes attributes) {
    super.setNameAndTooltip(presentation, name, tooltip, attributes);
    if (getProjectsNavigator().getShowVersions()) {
      presentation.addText(":" + myMavenProject.getMavenId().getVersion(),
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
