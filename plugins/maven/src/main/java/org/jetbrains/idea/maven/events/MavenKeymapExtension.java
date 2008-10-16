package org.jetbrains.idea.maven.events;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.runner.MavenRunConfigurationType;
import org.jetbrains.idea.maven.runner.MavenRunnerParameters;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenId;
import org.jetbrains.idea.maven.utils.MavenPluginInfo;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenKeymapExtension implements KeymapExtension {
  private static final Icon OPEN_ICON = IconLoader.getIcon("/images/phasesOpen.png");
  private static final Icon CLOSED_ICON = IconLoader.getIcon("/images/phasesClosed.png");

  public KeymapGroup createGroup(Condition<AnAction> condition, Project project) {
    KeymapGroup result = KeymapGroupFactory.getInstance().createGroup(
        EventsBundle.message("maven.event.action.group.name"), CLOSED_ICON, OPEN_ICON);

    if (project == null) return result;

    ActionManager actionManager = ActionManager.getInstance();
    String[] ids = actionManager.getActionIds(getActionPrefix(project, null));
    Arrays.sort(ids);

    Map<String, KeymapGroup> pomPathToActionId = new HashMap<String, KeymapGroup>();
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);

    for (String eachId : ids) {
      AnAction eachAction = actionManager.getAction(eachId);

      if (!(eachAction instanceof MavenGoalAction)) continue;
      if (condition != null && !condition.value(actionManager.getActionOrStub(eachId))) continue;

      String pomPath = ((MavenGoalAction)eachAction).myPomPath;
      KeymapGroup subGroup = pomPathToActionId.get(pomPath);

      if (subGroup == null) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(pomPath);
        if (file == null) continue;

        String name = getMavenProjectName(projectsManager, file);
        subGroup = KeymapGroupFactory.getInstance().createGroup(name);
        pomPathToActionId.put(pomPath, subGroup);
        result.addGroup(subGroup);
      }

      subGroup.addActionId(eachId);
    }

    return result;
  }

  public static void updateActions(@NotNull Project project, boolean delete) {
    String actionIdPrefix = getActionPrefix(project, null);
    if (delete) {
      doUpdateActions(actionIdPrefix, Collections.<MavenGoalAction>emptyList());
      return;
    }

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    MavenEventsManager eventsHandler = MavenEventsManager.getInstance(project);

    List<MavenGoalAction> actionList = new ArrayList<MavenGoalAction>();
    for (MavenProjectModel eachProject : projectsManager.getProjects()) {
      if (projectsManager.isIgnored(eachProject)) continue;
      actionList.addAll(collectActions(eachProject, projectsManager, eventsHandler));
    }

    doUpdateActions(actionIdPrefix, actionList);
  }

  public static void updateActions(@NotNull Project project, MavenProjectModel mavenProject, boolean delete) {
    String actionIdPrefix = getActionPrefix(project, mavenProject);
    if (delete) {
      doUpdateActions(actionIdPrefix, Collections.<MavenGoalAction>emptyList());
      return;
    }

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    MavenEventsManager eventsHandler = MavenEventsManager.getInstance(project);

    doUpdateActions(actionIdPrefix,  collectActions(mavenProject, projectsManager, eventsHandler));
  }

  private static List<MavenGoalAction> collectActions(MavenProjectModel eachProject,
                                                      MavenProjectsManager projectsManager,
                                                      MavenEventsManager eventsHandler) {
    List<MavenGoalAction> result = new ArrayList<MavenGoalAction>();
    String projectName = eachProject.getMavenProject().getName();
    String pomPath = eachProject.getPath();
    String actionIdPrefix = eventsHandler.getActionId(pomPath, null);

    for (String eachGoal : collectGoals(eachProject, projectsManager.getLocalRepository())) {
      result.add(new MavenGoalAction(projectName, actionIdPrefix, pomPath, eachGoal));
    }

    return result;
  }

  @TestOnly
  static String getActionPrefix(Project project, MavenProjectModel mavenProject) {
    String pomPath = mavenProject == null ? null : mavenProject.getPath();
    return MavenEventsManager.getInstance(project).getActionId(pomPath, null);
  }

  private static void doUpdateActions(String actionIdPrefix, Collection<MavenGoalAction> actionList) {
    ActionManager actionManager = ActionManager.getInstance();

    for (String oldId : actionManager.getActionIds(actionIdPrefix)) {
      actionManager.unregisterAction(oldId);
    }
    for (MavenGoalAction action : actionList) {
      actionManager.registerAction(action.myId, action);
    }
  }

  private static String getMavenProjectName(MavenProjectsManager projectsManager, VirtualFile file) {
    MavenProjectModel n = projectsManager.findProject(file);
    if (n != null) {
      return n.getMavenProject().getName();
    }
    return EventsBundle.message("maven.event.unknown.project");
  }

  private static Collection<String> collectGoals(MavenProjectModel project, File repository) {
    Collection<String> result = new HashSet<String>();
    result.addAll(MavenEmbedderFactory.getStandardGoalsList());

    for (MavenId plugin : project.getPluginIds()) {
      collectGoals(repository, plugin, result);
    }

    return result;
  }

  private static void collectGoals(File repository, MavenId mavenId, Collection<String> list) {
    MavenPluginInfo plugin = MavenArtifactUtil.readPluginInfo(repository, mavenId);
    if (plugin == null) return;

    for (MavenPluginInfo.Mojo m : plugin.getMojos()) {
      list.add(m.getQualifiedGoal());
    }
  }

  private static class MavenGoalAction extends AnAction {
    private final String myPomPath;
    private final String myGoal;
    private final String myId;

    public MavenGoalAction(String mavenProjectName, String actionIdPrefix, String pomPath, String goal) {
      myPomPath = pomPath;
      myGoal = goal;
      myId = actionIdPrefix + goal;

      Presentation template = getTemplatePresentation();
      template.setText(goal, false);
      template.setDescription(EventsBundle.message("maven.event.action.description", goal, mavenProjectName));
    }

    public void actionPerformed(AnActionEvent e) {
      Project project = e.getData(PlatformDataKeys.PROJECT);
      if (project == null) return;

      MavenRunnerParameters params = new MavenTask(myPomPath, myGoal).createRunnerParameters(MavenProjectsManager.getInstance(project));
      if (params == null) return;

      try {
        MavenRunConfigurationType.runConfiguration(project, params, e.getDataContext());
      }
      catch (ExecutionException ex) {
        MavenLog.LOG.warn(ex);
      }
    }

    public String toString() {
      return myId;
    }
  }
}
