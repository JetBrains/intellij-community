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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.indices.MavenPluginInfo;
import org.jetbrains.idea.maven.indices.MavenPluginsRepository;
import org.jetbrains.idea.maven.runner.MavenRunner;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

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
            String name = getMavenProjectName(MavenProjectsManager.getInstance(project),
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
    final MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    final MavenPluginsRepository repository = MavenPluginsRepository.getInstance(project);
    final MavenEventsHandler eventsHandler = project.getComponent(MavenEventsHandler.class);

    final List<MavenGoalAction> actionList = new ArrayList<MavenGoalAction>();

    for (MavenProjectModel each : projectsManager.getProjects()) {
      if (!projectsManager.isIgnored(each)) {
        final String mavenProjectName = each.getMavenProject().getName();
        final String pomPath = each.getPath();
        final String actionIdPrefix = eventsHandler.getActionId(pomPath, null);
        for (String goal : getGoals(each, repository)) {
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

  private static String getMavenProjectName(MavenProjectsManager projectsManager, VirtualFile file) {
    MavenProjectModel n = projectsManager.findProject(file);
    if (n != null) {
      return n.getMavenProject().getName();
    }
    return EventsBundle.message("maven.event.unknown.project");
  }

  private static Collection<String> getGoals(MavenProjectModel node, MavenPluginsRepository repository) {
    Collection<String> result = new HashSet<String>();
    result.addAll(MavenEmbedderFactory.getStandardGoalsList());

    for (MavenId plugin : node.getPluginIds()) {
      collectGoals(repository, plugin, result);
    }

    return result;
  }

  private static void collectGoals(final MavenPluginsRepository repository, final MavenId mavenId, final Collection<String> list) {
    final MavenPluginInfo plugin = repository.loadPluginInfo(mavenId);
    if (plugin == null) return;

    for (MavenPluginInfo.Mojo m : plugin.getMojos()) {
      list.add(m.getQualifiedGoal());
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
            new MavenTask(myPomPath, myGoal).createBuildParameters(MavenProjectsManager.getInstance(project));
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
