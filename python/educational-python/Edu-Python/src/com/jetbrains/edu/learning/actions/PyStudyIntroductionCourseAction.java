/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.edu.learning.actions;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.PyStudyDirectoryProjectGenerator;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.newProject.steps.ProjectSpecificSettingsStep;
import com.jetbrains.python.newProject.steps.PythonGenerateProjectCallback;
import icons.InteractiveLearningPythonIcons;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class PyStudyIntroductionCourseAction extends AnAction {

  public static final String INTRODUCTION_TO_PYTHON = "Introduction to Python";
  public static final String INTRODUCTION_FOLDER = "PythonIntroduction";

  public PyStudyIntroductionCourseAction() {
    super(INTRODUCTION_TO_PYTHON, INTRODUCTION_TO_PYTHON, InteractiveLearningPythonIcons.EducationalProjectType);
  }

  @Override
  public void update(AnActionEvent e) {
    final File projectDir = new File(ProjectUtil.getBaseDir(), INTRODUCTION_FOLDER);
    if (projectDir.exists()) {
      return;
    }
    final EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(PythonLanguage.getInstance());
    final List<String> paths = configurator.getBundledCoursePaths();
    if (paths.isEmpty()) {
      return;
    }
    Presentation presentation = e.getPresentation();
    presentation.setVisible(false);
    presentation.setEnabled(false);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final File projectDir = new File(ProjectUtil.getBaseDir(), INTRODUCTION_FOLDER);
    if (projectDir.exists()) {
      ProjectUtil.openProject(projectDir.getPath(), null, false);
    }
    else {
      final PyStudyDirectoryProjectGenerator generator = new PyStudyDirectoryProjectGenerator(true);
      final EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(PythonLanguage.getInstance());
      final String bundledCoursePath = configurator.getBundledCoursePaths().get(0);
      Course introCourse = StudyProjectGenerator.getCourse(bundledCoursePath);
      if (introCourse == null) {
        return;
      }
      final PythonGenerateProjectCallback callback = new PythonGenerateProjectCallback();
      final ProjectSpecificSettingsStep step = new ProjectSpecificSettingsStep(generator, callback);
      step.createPanel(); // initialize panel to set location
      step.setLocation(projectDir.toString());
      generator.setSelectedCourse(introCourse);

      callback.consume(step);
    }
  }
}
