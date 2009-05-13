package org.jetbrains.idea.maven.tasks;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.project.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.runner.MavenRunConfigurationType;
import org.jetbrains.idea.maven.runner.MavenRunnerParameters;
import org.jetbrains.idea.maven.utils.MavenAction;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenPluginInfo;

import javax.swing.*;
import java.io.File;
import java.util.*;

public class MavenKeymapExtension implements KeymapExtension {
  private static final Icon OPEN_ICON = IconLoader.getIcon("/images/phasesOpen.png");
  private static final Icon CLOSED_ICON = IconLoader.getIcon("/images/phasesClosed.png");

  public KeymapGroup createGroup(Condition<AnAction> condition, Project project) {
    KeymapGroup result = KeymapGroupFactory.getInstance().createGroup(TasksBundle.message("maven.event.action.group.name"),
                                                                      CLOSED_ICON,
                                                                      OPEN_ICON);
    if (project == null) return result;

    ActionManager actionManager = ActionManager.getInstance();
    String[] actionIds = actionManager.getActionIds(getActionPrefix(project, null));
    Arrays.sort(actionIds);

    Map<MavenProject, KeymapGroup> projectToGroupMapping = new THashMap<MavenProject, KeymapGroup>();
    for (String eachId : actionIds) {
      AnAction eachAction = actionManager.getAction(eachId);

      if (!(eachAction instanceof MavenGoalAction)) continue;
      if (condition != null && !condition.value(actionManager.getActionOrStub(eachId))) continue;

      MavenProject mavenProject = ((MavenGoalAction)eachAction).getMavenProject();
      KeymapGroup projectGroup = projectToGroupMapping.get(mavenProject);

      if (projectGroup == null) {
        projectGroup = KeymapGroupFactory.getInstance().createGroup(mavenProject.getDisplayName());
        projectToGroupMapping.put(mavenProject, projectGroup);
        result.addGroup(projectGroup);
      }

      projectGroup.addActionId(eachId);
    }

    return result;
  }

  public static void updateActions(Project project) {
    clearActions(project);
    doUpdateActions(project, MavenProjectsManager.getInstance(project).getNonIgnoredProjects());
  }

  public static void updateActions(Project project, List<MavenProject> mavenProjects) {
    clearActions(project, mavenProjects);
    doUpdateActions(project, mavenProjects);
  }

  private static void doUpdateActions(Project project, List<MavenProject> mavenProjects) {
    ActionManager manager = ActionManager.getInstance();
    for (MavenProject eachProject : mavenProjects) {
      String actionIdPrefix = getActionPrefix(project, eachProject);
      for (MavenGoalAction eachAction : collectActions(project, eachProject)) {
        manager.registerAction(actionIdPrefix + eachAction.getGoal(), eachAction);
      }
    }
  }

  private static List<MavenGoalAction> collectActions(Project project, MavenProject mavenProject) {
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);

    File localRepository = projectsManager.getLocalRepository();
    List<MavenGoalAction> result = new ArrayList<MavenGoalAction>();
    for (String eachGoal : collectGoals(mavenProject, localRepository)) {
      result.add(new MavenGoalAction(mavenProject, eachGoal));
    }
    return result;
  }

  public static void clearActions(Project project) {
    ActionManager manager = ActionManager.getInstance();
    for (String each : manager.getActionIds(getActionPrefix(project, null))) {
      manager.unregisterAction(each);
    }
  }

  public static void clearActions(Project project, List<MavenProject> mavenProjects) {
    ActionManager manager = ActionManager.getInstance();
    for (MavenProject eachProject : mavenProjects) {
      for (String eachAction : manager.getActionIds(getActionPrefix(project, eachProject))) {
        manager.unregisterAction(eachAction);
      }
    }
  }

  private static Collection<String> collectGoals(MavenProject project, File repository) {
    Collection<String> result = new THashSet<String>();
    result.addAll(MavenEmbedderFactory.getPhasesList());

    for (MavenPlugin each : project.getPlugins()) {
      collectGoals(repository, each, result);
    }

    return result;
  }

  private static void collectGoals(File repository, MavenPlugin plugin, Collection<String> list) {
    MavenPluginInfo info = MavenArtifactUtil.readPluginInfo(repository, plugin.getMavenId());
    if (info == null) return;

    for (MavenPluginInfo.Mojo m : info.getMojos()) {
      list.add(m.getQualifiedGoal());
    }
  }

  @TestOnly
  public static String getActionPrefix(Project project, MavenProject mavenProject) {
    String pomPath = mavenProject == null ? null : mavenProject.getPath();
    return MavenTasksManager.getInstance(project).getActionId(pomPath, null);
  }

  private static class MavenGoalAction extends MavenAction {
    private final MavenProject myMavenProject;
    private final String myGoal;

    public MavenGoalAction(MavenProject mavenProject, String goal) {
      myMavenProject = mavenProject;
      myGoal = goal;
      Presentation template = getTemplatePresentation();
      template.setText(goal, false);
    }

    public void actionPerformed(AnActionEvent e) {
      MavenRunnerParameters params = new MavenGoalTask(myMavenProject.getPath(), myGoal).createRunnerParameters(getProjectsManager(e));
      if (params == null) return;
      try {
        MavenRunConfigurationType.runConfiguration(getProject(e), params, e.getDataContext());
      }
      catch (ExecutionException ex) {
        MavenLog.LOG.warn(ex);
      }
    }

    public MavenProject getMavenProject() {
      return myMavenProject;
    }

    public String getGoal() {
      return myGoal;
    }

    public String toString() {
      return myMavenProject + ":" + myGoal;
    }
  }
}
