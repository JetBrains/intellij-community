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
package com.jetbrains.python.edu.actions;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.edu.StudyDirectoryProjectGenerator;
import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.CourseInfo;
import com.jetbrains.python.newProject.actions.GenerateProjectCallback;
import com.jetbrains.python.newProject.actions.ProjectSpecificSettingsStep;
import icons.StudyIcons;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Map;

public class StudyIntroductionCourseAction extends AnAction {

  public StudyIntroductionCourseAction() {
    super("Introduction to Python", "Introduction to Python", StudyIcons.EducationalProjectType);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final File projectDir = new File(ProjectUtil.getBaseDir(), "PythonIntroduction");
    if (projectDir.exists()) {
      ProjectUtil.openProject(projectDir.getPath(), null, false);
    }
    else {
      final GenerateProjectCallback callback = new GenerateProjectCallback(null);
      final StudyDirectoryProjectGenerator generator = new StudyDirectoryProjectGenerator();
      final Map<CourseInfo, File> courses = generator.getCourses();
      CourseInfo introCourse = null;
      for (CourseInfo info : courses.keySet()) {
        if ("Introduction to Python".equals(info.getName())) {
          introCourse = info;
        }
      }
      if (introCourse == null) {
        introCourse = StudyUtils.getFirst(courses.keySet());
      }
      generator.setSelectedCourse(introCourse);
      final ProjectSpecificSettingsStep step = new ProjectSpecificSettingsStep(generator, callback, true);

      step.createPanel(); // initialize panel to set location
      step.setLocation(projectDir.toString());

      final Project project = ProjectManager.getInstance().getDefaultProject();
      final List<Sdk> sdks = PyConfigurableInterpreterList.getInstance(project).getAllPythonSdks();
      Sdk sdk = sdks.isEmpty() ? null : sdks.iterator().next();
      step.setSdk(sdk);
      callback.consume(step);
    }
  }
}
