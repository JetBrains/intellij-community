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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.edu.learning.PyStudyDirectoryProjectGenerator;
import com.jetbrains.edu.stepic.CourseInfo;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.newProject.actions.GenerateProjectCallback;
import com.jetbrains.python.newProject.actions.ProjectSpecificSettingsStep;
import icons.InteractiveLearningPythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    final PyStudyDirectoryProjectGenerator generator = new PyStudyDirectoryProjectGenerator();
    if (getIntroCourseInfo(generator.getCourses()) != null) {
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
      final PyStudyDirectoryProjectGenerator generator = new PyStudyDirectoryProjectGenerator();
      CourseInfo introCourse = getIntroCourseInfo(generator.getCourses());
      if (introCourse == null) {
        return;
      }
      final GenerateProjectCallback callback = new GenerateProjectCallback();
      final ProjectSpecificSettingsStep step = new ProjectSpecificSettingsStep(generator, callback);
      step.createPanel(); // initialize panel to set location
      step.setLocation(projectDir.toString());
      generator.setSelectedCourse(introCourse);

      final Project project = ProjectManager.getInstance().getDefaultProject();
      final List<Sdk> sdks = PyConfigurableInterpreterList.getInstance(project).getAllPythonSdks();
      Sdk sdk = sdks.isEmpty() ? null : sdks.iterator().next();
      step.setSdk(sdk);
      callback.consume(step);
    }
  }

  @Nullable
  private static CourseInfo getIntroCourseInfo(final List<CourseInfo> courses) {
    for (CourseInfo courseInfo : courses) {
      if (INTRODUCTION_TO_PYTHON.equals(courseInfo.getName())) {
        return courseInfo;
      }
    }
    return null;
  }
}
