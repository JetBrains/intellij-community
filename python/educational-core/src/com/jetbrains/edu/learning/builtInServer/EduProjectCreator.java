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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.jetbrains.edu.learning.courseFormat.Course;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author meanmail
 */
public class EduProjectCreator {
  public static final ExtensionPointName<EduProjectCreator> EP_NAME = ExtensionPointName.create("Edu.eduProjectCreator");

  public static boolean createProject(@NotNull Course course, @Nullable Consumer<Project> callback) {
    EduProjectCreator[] extensions = Extensions.getExtensions(EP_NAME);

    for (EduProjectCreator projectCreator : extensions) {
      if (projectCreator.canCreateProject(course)) {
        return projectCreator.createCourseProject(course, callback);
      }
    }

    return false;
  }

  public boolean createCourseProject(@NotNull Course course, @Nullable Consumer<Project> callback) {
    return false;
  }

  public boolean canCreateProject(@NotNull Course course) {
    return false;
  }
}
