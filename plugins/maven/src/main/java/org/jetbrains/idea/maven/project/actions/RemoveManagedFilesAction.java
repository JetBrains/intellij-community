/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project.actions;

import com.intellij.CommonBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.idea.maven.utils.actions.MavenActionUtil.getProject;

public class RemoveManagedFilesAction extends MavenAction {
  @Override
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    if (!super.isAvailable(e)) return false;
    return MavenActionUtil.getMavenProjectsFiles(e.getDataContext()).size() > 0;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext context = e.getDataContext();

    final Project project = getProject(context);
    if (project == null) {
      return;
    }
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);

    List<VirtualFile> selectedFiles = MavenActionUtil.getMavenProjectsFiles(context);
    List<VirtualFile> removableFiles = new ArrayList<>();
    List<String> filesToUnIgnore = new ArrayList<>();

    List<Module> modulesToRemove = new ArrayList<>();

    for (VirtualFile pomXml : selectedFiles) {
      if (projectsManager.isManagedFile(pomXml)) {
        MavenProject managedProject = projectsManager.findProject(pomXml);
        if (managedProject == null) {
          continue;
        }
        addModuleToRemoveList(projectsManager, modulesToRemove, managedProject);
        projectsManager.getModules(managedProject).forEach(mp -> {
          addModuleToRemoveList(projectsManager, modulesToRemove, mp);
          filesToUnIgnore.add(mp.getFile().getPath());

        });
        removableFiles.add(pomXml);
        filesToUnIgnore.add(pomXml.getPath());
      }
      else {
        notifyUserIfNeeded(context, projectsManager, selectedFiles, pomXml);
      }
    }
    List<String> names = ContainerUtil.map(modulesToRemove, m -> m.getName());
    int returnCode =
      Messages
        .showOkCancelDialog(ExternalSystemBundle.message("action.detach.external.confirmation.prompt", "Maven", names.size(), names),
                            getActionTitle(names),
                            CommonBundle.message("button.remove"), CommonBundle.getCancelButtonText(),
                            Messages.getQuestionIcon());
    if (returnCode != Messages.OK) {
      return;
    }

    removeModules(ModuleManager.getInstance(project), projectsManager, modulesToRemove);
    projectsManager.removeManagedFiles(removableFiles);
    projectsManager.removeIgnoredFilesPaths(filesToUnIgnore); // hack to remove deleted files from ignore list
  }


  @Nls
  private static String getActionTitle(List<String> names) {
    return StringUtil.pluralize(ExternalSystemBundle.message("action.detach.external.project.text", "Maven"), names.size());
  }

  private static void addModuleToRemoveList(MavenProjectsManager manager, List<Module> modulesToRemove, MavenProject project) {
    Module module = manager.findModule(project);
    if (module == null) {
      return;
    }
    modulesToRemove.add(module);
  }

  private static void removeModules(ModuleManager moduleManager, MavenProjectsManager mavenProjectsManager,  List<Module> modulesToRemove) {
    WriteAction.run(() -> {
      List<ModifiableRootModel> usingModels = new SmartList<>();

      for (Module module : modulesToRemove) {

        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        for (OrderEntry entry : moduleRootManager.getOrderEntries()) {
          if (entry instanceof ModuleOrderEntry) {
            usingModels.add(moduleRootManager.getModifiableModel());
            break;
          }
        }
      }


      final ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
      for (Module module : modulesToRemove) {
        ModuleDeleteProvider.removeModule(module, usingModels, moduleModel);
      }
      ModifiableModelCommitter.multiCommit(usingModels, moduleModel);
    });
  }

  private static void notifyUserIfNeeded(DataContext context,
                                         MavenProjectsManager projectsManager,
                                         List<VirtualFile> selectedFiles,
                                         VirtualFile pomXml) {
    MavenProject mavenProject = projectsManager.findProject(pomXml);
    assert mavenProject != null;

    MavenProject aggregator = projectsManager.findAggregator(mavenProject);
    while (aggregator != null && !projectsManager.isManagedFile(aggregator.getFile())) {
      aggregator = projectsManager.findAggregator(aggregator);
    }

    if (aggregator != null && !selectedFiles.contains(aggregator.getFile())) {
      notifyUser(context, mavenProject, aggregator);
    }
  }

  private static void notifyUser(DataContext context, MavenProject mavenProject, MavenProject aggregator) {
    String aggregatorDescription = " (" + aggregator.getMavenId().getDisplayString() + ')';
    Notification notification =
      new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, MavenProjectBundle.message("maven.module.remove.failed"),
                       MavenProjectBundle
                         .message("maven.module.remove.failed.explanation", mavenProject.getDisplayName(), aggregatorDescription),
                       NotificationType.ERROR
      );

    notification.setImportant(true);
    notification.notify(getProject(context));
  }
}