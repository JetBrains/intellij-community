// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.tasks;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemKeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.keymap.impl.ui.Hyperlink;
import com.intellij.openapi.keymap.impl.ui.KeymapListener;
import com.intellij.openapi.keymap.impl.ui.KeymapPanel;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.navigator.MavenProjectsStructure;
import org.jetbrains.idea.maven.navigator.SelectMavenGoalDialog;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenPluginInfo;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;

import static icons.ExternalSystemIcons.Task;
import static icons.OpenapiIcons.RepositoryLibraryLogo;

public final class MavenKeymapExtension implements ExternalSystemKeymapExtension.ActionsProvider {
  @Override
  public KeymapGroup createGroup(Condition<? super AnAction> condition, final Project project) {
    KeymapGroup result = KeymapGroupFactory.getInstance().createGroup(TasksBundle.message("maven.tasks.action.group.name"), RepositoryLibraryLogo);
    if (project == null) return result;

    Comparator<MavenProject> projectComparator = (o1, o2) -> o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
    Map<MavenProject, Set<Pair<String, String>>> projectToActionsMapping
      = new TreeMap<>(projectComparator);

    ActionManager actionManager = ActionManager.getInstance();
    //noinspection TestOnlyProblems
    for (String eachId : actionManager.getActionIds(getActionPrefix(project, null))) {
      AnAction eachAction = actionManager.getAction(eachId);

      if (!(eachAction instanceof MavenGoalAction)) continue;
      if (condition != null && !condition.value(actionManager.getActionOrStub(eachId))) continue;

      MavenGoalAction mavenAction = (MavenGoalAction)eachAction;
      MavenProject mavenProject = mavenAction.getMavenProject();
      Set<Pair<String, String>> actions = projectToActionsMapping.get(mavenProject);
      if (actions == null) {
        final List<String> projectGoals = collectGoals(mavenProject);
        actions = new TreeSet<>((o1, o2) -> {
          String goal1 = o1.getFirst();
          String goal2 = o2.getFirst();
          int index1 = projectGoals.indexOf(goal1);
          int index2 = projectGoals.indexOf(goal2);
          if (index1 == index2) return goal1.compareToIgnoreCase(goal2);
          return index1 < index2 ? -1 : 1;
        });
        projectToActionsMapping.put(mavenProject, actions);
      }
      actions.add(Pair.create(mavenAction.getGoal(), eachId));
    }

    for (Map.Entry<MavenProject, Set<Pair<String, String>>> each : projectToActionsMapping.entrySet()) {
      Set<Pair<String, String>> goalsToActionIds = each.getValue();
      for (Pair<String, String> eachGoalToActionId : goalsToActionIds) {
        result.addActionId(eachGoalToActionId.getSecond());
      }
    }

    Icon icon = AllIcons.General.Add;
    ((Group)result).addHyperlink(new Hyperlink(icon, "Choose a phase/goal to assign a shortcut") {
      @Override
      public void onClick(MouseEvent e) {
        SelectMavenGoalDialog dialog = new SelectMavenGoalDialog(project);
        if (dialog.showAndGet() && dialog.getResult() != null) {
          MavenProjectsStructure.GoalNode goalNode = dialog.getResult();
          String goal = goalNode.getGoal();
          String actionId = MavenShortcutsManager.getInstance(project).getActionId(goalNode.getProjectPath(), goal);
          getOrRegisterAction(goalNode.getMavenProject(), actionId, goal);

          ApplicationManager.getApplication().getMessageBus().syncPublisher(KeymapListener.CHANGE_TOPIC).processCurrentKeymapChanged();
          Settings allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(e.getComponent()));
          KeymapPanel keymapPanel = allSettings != null ? allSettings.find(KeymapPanel.class) : null;
          if (keymapPanel != null) {
            // clear actions filter
            keymapPanel.showOption("");
            keymapPanel.selectAction(actionId);
          }
        }
      }
    });

    return result;
  }

  static void updateActions(Project project, List<? extends MavenProject> mavenProjects) {
    clearActions(project, mavenProjects);
    createActions(project, mavenProjects);
  }

  public static MavenAction getOrRegisterAction(MavenProject mavenProject, String actionId, String goal) {
    MavenGoalAction mavenGoalAction = new MavenGoalAction(mavenProject, goal);
    ActionManager manager = ActionManager.getInstance();
    AnAction anAction = manager.getAction(actionId);
    if (anAction instanceof MavenGoalAction) {
      return (MavenGoalAction)anAction;
    }
    manager.replaceAction(actionId, mavenGoalAction);
    return mavenGoalAction;
  }

  private static void createActions(Project project, List<? extends MavenProject> mavenProjects) {
    ActionManager actionManager = ActionManager.getInstance();
    MavenShortcutsManager shortcutsManager = MavenShortcutsManager.getInstance(project);
    for (MavenProject eachProject : mavenProjects) {
      //noinspection TestOnlyProblems
      String actionIdPrefix = getActionPrefix(project, eachProject);
      for (MavenGoalAction eachAction : collectActions(eachProject)) {
        String id = actionIdPrefix + eachAction.getGoal();
        if(shortcutsManager.hasShortcuts(eachProject, eachAction.getGoal())) {
          actionManager.replaceAction(id, eachAction);
        }
        else {
          actionManager.unregisterAction(id);
        }
      }
    }
  }

  private static List<MavenGoalAction> collectActions(MavenProject mavenProject) {
    List<MavenGoalAction> result = new ArrayList<>();
    for (String eachGoal : collectGoals(mavenProject)) {
      result.add(new MavenGoalAction(mavenProject, eachGoal));
    }
    return result;
  }

  public static void clearActions(Project project) {
    ActionManager manager = ActionManager.getInstance();
    //noinspection TestOnlyProblems
    for (String each : manager.getActionIds(getActionPrefix(project, null))) {
      manager.unregisterAction(each);
    }
  }

  static void clearActions(Project project, List<? extends MavenProject> mavenProjects) {
    ActionManager manager = ActionManager.getInstance();
    for (MavenProject eachProject : mavenProjects) {
      //noinspection TestOnlyProblems
      for (String eachAction : manager.getActionIds(getActionPrefix(project, eachProject))) {
        manager.unregisterAction(eachAction);
      }
    }
  }

  private static List<String> collectGoals(MavenProject project) {
    LinkedHashSet<String> result = new LinkedHashSet<>(MavenConstants.PHASES); // may contains similar plugins or something

    for (MavenPlugin each : project.getDeclaredPlugins()) {
      collectGoals(project.getLocalRepository(), each, result);
    }

    return new ArrayList<>(result);
  }

  private static void collectGoals(File repository, MavenPlugin plugin, Set<? super String> list) {
    MavenPluginInfo info = MavenArtifactUtil.readPluginInfo(repository, plugin.getMavenId());
    if (info == null) return;

    for (MavenPluginInfo.Mojo m : info.getMojos()) {
      list.add(m.getQualifiedGoal());
    }
  }

  @TestOnly
  public static String getActionPrefix(Project project, MavenProject mavenProject) {
    String pomPath = mavenProject == null ? null : mavenProject.getPath();
    return MavenShortcutsManager.getInstance(project).getActionId(pomPath, null);
  }

  private static class MavenGoalAction extends MavenAction {
    private final MavenProject myMavenProject;
    private final String myGoal;

    MavenGoalAction(MavenProject mavenProject, String goal) {
      myMavenProject = mavenProject;
      myGoal = goal;
      Presentation template = getTemplatePresentation();
      template.setText(goal + " (" + mavenProject.getMavenId() + ")", false);
      template.setIcon(Task);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final DataContext context = e.getDataContext();
      final Project project = MavenActionUtil.getProject(context);
      if (project == null) return;

      final MavenProjectsManager projectsManager = MavenActionUtil.getProjectsManager(context);
      if(projectsManager == null) return;
      MavenExplicitProfiles explicitProfiles = projectsManager.getExplicitProfiles();
      MavenRunnerParameters params = new MavenRunnerParameters(true,
                                                               myMavenProject.getDirectory(),
                                                               myMavenProject.getFile().getName(),
                                                               Collections.singletonList(myGoal),
                                                               explicitProfiles.getEnabledProfiles(),
                                                               explicitProfiles.getDisabledProfiles());
      MavenRunConfigurationType.runConfiguration(project, params, null);
    }

    public MavenProject getMavenProject() {
      return myMavenProject;
    }

    public String getGoal() {
      return myGoal;
    }

    @Override
    public String toString() {
      return myMavenProject + ":" + myGoal;
    }
  }
}
