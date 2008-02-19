package org.jetbrains.idea.maven.events;

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
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.runner.MavenRunner;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;
import org.jetbrains.idea.maven.core.util.MavenEnv;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.repo.MavenRepository;
import org.jetbrains.idea.maven.repo.PluginDocument;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import javax.swing.*;
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenKeymapExtension implements KeymapExtension {

  private static final Icon iconOpen = IconLoader.getIcon("/images/phasesOpen.png");
  private static final Icon iconClosed = IconLoader.getIcon("/images/phasesClosed.png");

  public KeymapGroup createGroup(Condition<AnAction> condition, Project project) {
    final Map<String, KeymapGroup> pomPathToActionId = new HashMap<String, KeymapGroup>();
    final KeymapGroup result = KeymapGroupFactory.getInstance().createGroup(EventsBundle.message("maven.event.action.group.name"),
                                                                            iconClosed, iconOpen);

    if (project != null) {
      final ActionManager actionManager = ActionManager.getInstance();
      String[] ids = actionManager.getActionIds(getProjectPrefix(project));
      Arrays.sort(ids);

      for (final String id : ids) {
        if (condition != null && !condition.value(actionManager.getActionOrStub(id))) continue;
        final AnAction anAction = actionManager.getAction(id);
        if (anAction instanceof MavenGoalAction) {
          final String pomPath = ((MavenGoalAction)anAction).myPomPath;
          KeymapGroup subGroup = pomPathToActionId.get(pomPath);
          if (subGroup == null) {
            String name = getMavenProjectName(MavenProjectsState.getInstance(project),
                                              LocalFileSystem.getInstance().findFileByPath(pomPath));
            subGroup = KeymapGroupFactory.getInstance().createGroup(name);
            pomPathToActionId.put(pomPath, subGroup);
            result.addGroup(subGroup);
          }
          subGroup.addActionId(id);
        }
      }
    }

    return result;
  }

  private static String getProjectPrefix(@NotNull Project project) {
    return project.getComponent(MavenEventsHandler.class).getActionId(null, null);
  }

  public static void createActions(@NotNull Project project) {
    final MavenProjectsState projectsState = MavenProjectsState.getInstance(project);
    final MavenRepository repository = project.getComponent(MavenRepository.class);
    final MavenEventsHandler eventsHandler = project.getComponent(MavenEventsHandler.class);

    final List<MavenGoalAction> actionList = new ArrayList<MavenGoalAction>();

    for (VirtualFile file : projectsState.getFiles()) {
      if (!projectsState.isIgnored(file)) {
        final String mavenProjectName = getMavenProjectName(projectsState, file);
        final String pomPath = file.getPath();
        final String actionIdPrefix = eventsHandler.getActionId(pomPath, null);
        for (String goal : getGoals(projectsState, repository, file)) {
          actionList.add(new MavenGoalAction(mavenProjectName, actionIdPrefix, pomPath, goal));
        }
      }
    }

    refreshActions(actionList, getProjectPrefix(project));
  }

  public static void clearActions(@NotNull Project project) {
    refreshActions(new ArrayList<MavenGoalAction>(), getProjectPrefix(project));
  }

  private static void refreshActions(Collection<MavenGoalAction> actionList, String actionIdPrefix) {
    final ActionManager actionManager = ActionManager.getInstance();
    synchronized (actionManager) {
      for (String oldId : actionManager.getActionIds(actionIdPrefix)) {
        actionManager.unregisterAction(oldId);
      }

      for (MavenGoalAction action : actionList) {
        actionManager.registerAction(action.myId, action);
      }
    }
  }

  private static String getMavenProjectName(MavenProjectsState projectsState, VirtualFile file) {
    if (file != null) {
      final MavenProject mavenProject = projectsState.getMavenProject(file);
      if (mavenProject != null) {
        return mavenProject.getName();
      }
    }
    return EventsBundle.message("maven.event.unknown.project");
  }

  private static Collection<String> getGoals(MavenProjectsState projectsState, MavenRepository repository, VirtualFile file) {
    Collection<String> list = new HashSet<String>();
    list.addAll(MavenEnv.getStandardGoalsList());
    for (MavenId mavenId : ProjectUtil
      .collectPluginIds(projectsState.getMavenProject(file), projectsState.getProfiles(file), new HashSet<MavenId>())) {
      collectGoals(repository, mavenId, list);
    }
    for (MavenId mavenId : projectsState.getAttachedPlugins(file)) {
      collectGoals(repository, mavenId, list);
    }
    return list;
  }

  private static void collectGoals(final MavenRepository repository, final MavenId mavenId, final Collection<String> list) {
    final PluginDocument pluginDocument = repository.loadPlugin(mavenId);
    if (pluginDocument != null) {
      final PluginDocument.Plugin plugin = pluginDocument.getPlugin();
      for (PluginDocument.Mojo mojo : plugin.getMojos().getMojoList()) {
        list.add(plugin.getGoalPrefix() + ":" + mojo.getGoal());
      }
    }
  }

  static class MavenGoalAction extends AnAction {
    private final String myPomPath;
    private final String myGoal;
    private final String myId;

    public MavenGoalAction(String mavenProjectName, String actionIdPrefix, String pomPath, String goal) {
      myPomPath = pomPath;
      myGoal = goal;
      myId = actionIdPrefix + goal;

      Presentation templatePresentation = getTemplatePresentation();
      templatePresentation.setText(goal, false);
      templatePresentation.setDescription(EventsBundle.message("maven.event.action.description", goal, mavenProjectName));
    }

    public void actionPerformed(AnActionEvent e) {
      final Project project = e.getData(PlatformDataKeys.PROJECT);
      if (project != null) {
        final MavenRunner runner = project.getComponent(MavenRunner.class);
        if (!runner.isRunning()) {
          final MavenRunnerParameters runnerParameters =
            new MavenTask(myPomPath, myGoal).createBuildParameters(MavenProjectsState.getInstance(project));
          if (runnerParameters != null) {
            runner.run(runnerParameters);
          }
        }
      }
    }

    public String toString() {
      return myId;
    }
  }
}
