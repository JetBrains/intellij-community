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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.stepic.EduStepicAuthorizedClient;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.stepic.StepicUser;

import java.io.IOException;

import static com.jetbrains.edu.learning.StudyUtils.execCancelable;

public class EduCreateNewStepikProjectDialog extends EduCreateNewProjectDialog {
  private static final Logger LOG = Logger.getInstance(EduCreateNewStepikProjectDialog.class);

  public EduCreateNewStepikProjectDialog() {
    super();
  }

  public EduCreateNewStepikProjectDialog(int courseId) {
    this();

    StepicUser user = EduStepicAuthorizedClient.getCurrentUser();
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    ApplicationManager.getApplication().invokeAndWait(() ->
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(() -> {
          ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
          execCancelable(() -> {
            try {
              Course course = EduStepicConnector.getCourseFromStepik(user, courseId);
              if (course != null) {
                setTitle("New Project - " + course.getName());
              }

              setCourse(course);
            }
            catch (IOException e) {
              LOG.warn("Tried to create a project for course with id=" + courseId, e);
            }
            return null;
          });
        }, "Getting Available Courses", true, defaultProject)
    );
  }

  @Override
  public void show() {
    if (myCourse != null) {
      super.show();
    } else {
      doCancelAction();
    }
  }
}
