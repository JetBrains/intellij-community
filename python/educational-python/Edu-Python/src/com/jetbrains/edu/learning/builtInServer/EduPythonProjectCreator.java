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
package com.jetbrains.edu.learning.builtInServer;

import com.intellij.ide.util.projectWizard.AbstractNewProjectDialog;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * @author meanmail
 */
public class EduPythonProjectCreator extends EduProjectCreator {
  @Override
  public boolean canCreateProject(@NotNull Course course) {
    return course.getLanguageById() == PythonLanguage.getInstance();
  }

  @Override
  public boolean createCourseProject(@NotNull Course course) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      AbstractNewProjectDialog dlg = new AbstractNewProjectDialog() {
        @Override
        protected DefaultActionGroup createRootStep() {
          return new BuiltInServerNewProjectStep(course);
        }
      };
      dlg.show();
    });
    return true;
  }
}
