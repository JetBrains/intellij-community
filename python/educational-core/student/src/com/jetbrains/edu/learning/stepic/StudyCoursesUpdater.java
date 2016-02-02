/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.edu.learning.stepic;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.stepic.CourseInfo;
import com.jetbrains.edu.stepic.EduStepicConnector;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StudyCoursesUpdater implements StartupActivity {

  public static StudyCoursesUpdater getInstance() {
    final StartupActivity[] extensions = Extensions.getExtensions(StartupActivity.POST_STARTUP_ACTIVITY);
    for (StartupActivity extension : extensions) {
      if (extension instanceof StudyCoursesUpdater) {
        return (StudyCoursesUpdater) extension;
      }
    }
    throw new UnsupportedOperationException("could not find self");
  }

  @Override
  public void runActivity(@NotNull final Project project) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      return;
    }
    if (checkNeeded()) {
      application.executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          final List<CourseInfo> courses = EduStepicConnector.getCourses();
          StudyProjectGenerator.flushCache(courses);
        }
      });
    }
  }


  public static boolean checkNeeded() {
    final List<CourseInfo> courses = StudyProjectGenerator.getCoursesFromCache();
    return courses.isEmpty();
  }
}
