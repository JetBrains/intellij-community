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
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.platform.DirectoryProjectGenerator;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.newproject.EduCourseProjectGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class EduCreateNewProjectDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(EduCreateNewProjectDialog.class);
  protected Project myProject;
  protected Course myCourse;
  private final EduCreateNewProjectPanel myPanel;

  public EduCreateNewProjectDialog() {
    super(false);
    setTitle("New Project");
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    myPanel = new EduCreateNewProjectPanel(defaultProject, this);
    setOKButtonText("Create");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public void setCourse(@Nullable Course course) {
    myCourse = course;
    String description = course != null ? course.getDescription() : "";
    myPanel.setDescription(description);
  }

  @Override
  protected void doOKAction() {
    if (myCourse == null) {
      myPanel.setError("Selected course is null");
      return;
    }
    Language language = myCourse.getLanguageById();
    if (language == null) {
      String message = "Selected course don't have language";
      myPanel.setError(message);
      LOG.warn(message);
      return;
    }
    EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(language);
    if (configurator == null) {
      String message = "A configurator for the selected course not found";
      myPanel.setError(message);
      LOG.warn(message + ": " + language);
      return;
    }
    EduCourseProjectGenerator projectGenerator = configurator.getEduCourseProjectGenerator();
    ValidationResult result = projectGenerator.validate();
    if (!result.isOk()) {
      myPanel.setError(result.getErrorMessage());
      return;
    }
    createProject(projectGenerator, myCourse, myPanel.getLocationPath());
  }

  public static void createProject(@NotNull EduCourseProjectGenerator projectGenerator, @NotNull Course course, @NotNull String location) {
    projectGenerator.setCourse(course);
    if (!projectGenerator.beforeProjectGenerated()) {
      return;
    }
    DirectoryProjectGenerator directoryProjectGenerator = projectGenerator.getDirectoryProjectGenerator();
    Project createdProject = AbstractNewProjectStep.doGenerateProject(null, location, directoryProjectGenerator, projectGenerator.getProjectSettings());
    if (createdProject == null) {
      return;
    }
    projectGenerator.afterProjectGenerated(createdProject);
  }
}
