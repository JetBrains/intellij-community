// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.ide.actions.runAnything.activity.RunAnythingProviderBase;
import com.intellij.ide.actions.runAnything.items.RunAnythingHelpItem;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.ide.actions.runAnything.items.RunAnythingItemBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.execution.ParametersListUtil;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenPluginInfo;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject;
import static com.intellij.openapi.util.text.StringUtil.*;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * @author ibessonov
 */
public class MavenRunAnythingProvider extends RunAnythingProviderBase<String> {

  @NotNull
  @Override
  public RunAnythingItem getMainListItem(@NotNull DataContext dataContext, @NotNull String value) {
    return new RunAnythingItemBase(getCommand(value), getIcon(value));
  }

  @NotNull
  @Override
  public Collection<String> getValues(@NotNull DataContext dataContext, @NotNull String pattern) {
    Project project = fetchProject(dataContext);
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    if (!projectsManager.isMavenizedProject()) {
      return emptyList();
    }

    if (!pattern.startsWith(getHelpCommand())) {
      return emptyList();
    }

    List<MavenProject> mavenProjects = projectsManager.getRootProjects();
    boolean onlyOneMavenProject = mavenProjects.size() == 1;

    List<String> values = new ArrayList<>();
    List<String> params = split(pattern, " ").stream().skip(1).map(String::trim).collect(toList());
    if (!onlyOneMavenProject && (params.isEmpty() || params.size() == 1 && !pattern.endsWith(" "))) {

      // list maven modules
      for (MavenProject mavenProject : mavenProjects) {
        Module module = projectsManager.findModule(mavenProject);
        if (module != null) {
          values.add(getHelpCommand() + " " + module.getName());
        }
      }
      if (params.isEmpty()) {
        return values;
      }
    }

    HashSet<String> goals = new HashSet<>(params.subList(onlyOneMavenProject ? 0 : 1, params.size()));
    Module module = onlyOneMavenProject ? projectsManager.findModule(mavenProjects.get(0))
                                        : ModuleManager.getInstance(project).findModuleByName(params.get(0));
    if (module != null) {
      MavenProject mavenProject = projectsManager.findProject(module);
      if (mavenProject != null) {
        String prefix = notNullize(substringBeforeLast(pattern, " "), getHelpCommand()).trim() + " ";
        if (!onlyOneMavenProject && prefix.trim().equals(getHelpCommand())) {
          prefix = prefix + module.getName() + " ";
        }

        // list basic phases
        for (String phase : MavenConstants.BASIC_PHASES) {
          if (!goals.contains(phase)) {
            values.add(prefix + phase);
          }
        }

        // list plugin-specific goals
        for (MavenPlugin mavenPlugin : mavenProject.getDeclaredPlugins()) {
          MavenPluginInfo pluginInfo = MavenArtifactUtil.readPluginInfo(projectsManager.getLocalRepository(), mavenPlugin.getMavenId());
          if (pluginInfo == null) continue;

          for (MavenPluginInfo.Mojo mojo : pluginInfo.getMojos()) {
            if (!goals.contains(mojo.getGoal())) {
              values.add(prefix + mojo.getDisplayName());
            }
          }
        }
      }
    }
    return values;
  }

  @Nullable
  @Override
  public String findMatchingValue(@NotNull DataContext dataContext, @NotNull String pattern) {
    return pattern.startsWith(getHelpCommand()) ? getCommand(pattern) : null;
  }

  @NotNull
  @Override
  public String getCommand(@NotNull String value) {
    return value;
  }

  @NotNull
  @Override
  public String getHelpCommand() {
    return "mvn";
  }

  @Nullable
  @Override
  public Icon getHelpIcon() {
    return MavenIcons.MavenLogo;
  }


  @NotNull
  @Override
  public String getCompletionGroupTitle() {
    return "Maven goals";
  }

  @Nullable
  @Override
  public RunAnythingHelpItem getHelpItem(@NotNull DataContext dataContext) {
    String placeholder = getHelpCommandPlaceholder(dataContext);
    String commandPrefix = getHelpCommand();
    return new RunAnythingHelpItem(placeholder, commandPrefix, getHelpDescription(), getHelpIcon());
  }

  @NotNull
  @Override
  public String getHelpCommandPlaceholder() {
    return getHelpCommandPlaceholder(null);
  }

  public String getHelpCommandPlaceholder(@Nullable DataContext dataContext) {
    if (dataContext != null) {
      Project project = fetchProject(dataContext);
      MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
      if (projectsManager.isMavenizedProject()) {
        if (projectsManager.getRootProjects().size() > 1) {
          return "mvn <moduleName> <goal...>";
        }
        else {
          return "mvn <goal...>";
        }
      }
    }
    return "mvn <moduleName?> <goal...>";
  }

  @Override
  public void execute(@NotNull DataContext dataContext, @NotNull String value) {
    Project project = fetchProject(dataContext);
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    if (!projectsManager.isMavenizedProject()) {
      return;
    }

    List<MavenProject> mavenProjects = projectsManager.getRootProjects();
    boolean onlyOneMavenProject = mavenProjects.size() == 1;

    List<String> goals = new ArrayList<>(ParametersListUtil.parse(trimStart(value, getHelpCommand()).trim()));
    if (!goals.isEmpty()) {

      MavenProject mavenProject = null;
      if (onlyOneMavenProject) {
        mavenProject = mavenProjects.get(0);
      }
      else {
        String moduleName = goals.remove(0);
        Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
        if (module != null) {
          mavenProject = projectsManager.findProject(module);
        }
      }

      if (mavenProject != null) {
        MavenExplicitProfiles explicitProfiles = projectsManager.getExplicitProfiles();
        MavenRunnerParameters params = new MavenRunnerParameters(true,
                                                                 mavenProject.getDirectory(),
                                                                 mavenProject.getFile().getName(),
                                                                 goals,
                                                                 explicitProfiles.getEnabledProfiles(),
                                                                 explicitProfiles.getDisabledProfiles());

        MavenRunner.getInstance(project).run(params, null, null);
      }
    }
  }
}
