/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.edu.learning.newproject.ui;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.platform.templates.TemplateProjectDirectoryGenerator;
import com.intellij.projectImport.ProjectOpenedCallback;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.newproject.EduCourseProjectGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author meanmail
 */
public class EduCreateNewProjectDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(EduCreateNewProjectDialog.class);
  private Project project;
  private Course course;
  private final List<EduCreateNewProjectListener> listeners = new ArrayList<>();
  private final EduCreateNewProjectPanel panel;

  public EduCreateNewProjectDialog() {
    super(false);
    setTitle("New project");
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    panel = new EduCreateNewProjectPanel(defaultProject, this);
    setOKButtonText("Create");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return panel;
  }

  public void addListener(@NotNull EduCreateNewProjectListener listener) {
    listeners.add(listener);
  }

  public void setCourse(@Nullable Course course) {
    this.course = course;
  }

  @Override
  protected void doOKAction() {
    if (course == null) {
      panel.setError("Selected course is null");
      return;
    }
    Language language = course.getLanguageById();
    if (language == null) {
      String message = "Selected course don't have language";
      panel.setError(message);
      LOG.warn(message);
      return;
    }
    EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(language);
    if (configurator == null) {
      String message = "A configurator for the selected course not found";
      panel.setError(message);
      LOG.warn(message + ": " + language);
      return;
    }
    EduCourseProjectGenerator projectGenerator = configurator.getEduCourseProjectGenerator();
    String errorMessage = createProject(projectGenerator);
    if (errorMessage != null) {
      panel.setError(errorMessage);
      return;
    }
    if (project == null) {
      panel.setError("Project did't created");
      return;
    }
    notifyCreated();
    super.doOKAction();
  }


  /**
   * @param projectGenerator
   * @return error message if didn't create project else return null
   */
  @Nullable
  private String createProject(@NotNull final EduCourseProjectGenerator projectGenerator) {
    String name = panel.getName();
    String path = FileUtil.join(FileUtil.toSystemDependentName(panel.getLocationPath()), name);

    ValidationResult result = projectGenerator.validate(path);
    if (!result.isOk()) {
      return result.getErrorMessage();
    }

    final File location = new File(path);
    if (!location.exists() && !location.mkdirs()) {
      String message = "Can't create a project directory";
      LOG.error(message + ": " + path);
      return message;
    }

    projectGenerator.setCourse(course);

    final VirtualFile baseDir = ApplicationManager.getApplication()
      .runWriteAction((Computable<VirtualFile>)() ->
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(location)
      );

    if (baseDir == null) {
      LOG.error("Couldn't find '" + location + "' in VFS");
      return "Couldn't find in VFS";
    }
    VfsUtil.markDirtyAndRefresh(false, true, true, baseDir);

    if (baseDir.getChildren().length > 0) {
      String message =
        String.format("Directory '%s' is not empty.\nFiles and directories will remove.\nDo you want continue?", location.getAbsolutePath());
      int rc = Messages.showYesNoDialog((Project)null, message, "New project", Messages.getQuestionIcon());
      if (rc != Messages.YES) {
        panel.resetError();
        return "Canceled by user";
      }
    }

    DirectoryProjectGenerator generator = projectGenerator.getDirectoryProjectGenerator();

    String generatorName = ConvertUsagesUtil.ensureProperKey(generator.getName());
    UsageTrigger.trigger("AbstractNewProjectStep." + generatorName);

    RecentProjectsManager.getInstance().setLastProjectCreationLocation(location.getParent());

    ProjectOpenedCallback callback = null;
    if(generator instanceof TemplateProjectDirectoryGenerator){
      ((TemplateProjectDirectoryGenerator)generator).generateProject(baseDir.getName(), path);
    } else {
      callback = (project, module) -> {
        if (projectGenerator.beforeProjectGenerated()) {
          Object settings = projectGenerator.getProjectSettings();
          generator.generateProject(project, baseDir, settings, module);
          projectGenerator.afterProjectGenerated(project);
        }
      };
    }
    EnumSet<PlatformProjectOpenProcessor.Option> options = EnumSet.noneOf(PlatformProjectOpenProcessor.Option.class);
    project = PlatformProjectOpenProcessor.doOpenProject(baseDir, null, -1, callback, options);
    return null;
  }

  private void notifyCreated() {
    listeners.forEach(listener -> listener.created(project));
  }
}
