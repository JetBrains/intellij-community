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

import com.intellij.openapi.project.Project;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.util.Consumer;
import com.jetbrains.edu.learning.PyStudyDirectoryProjectGenerator;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.python.newProject.steps.PyCharmNewProjectStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author meanmail
 */
public class EduBuiltInServerNewProjectStep extends PyCharmNewProjectStep {
  public EduBuiltInServerNewProjectStep(@NotNull Course course, @Nullable Consumer<Project> onCreated) {
    super(new EduBuiltInServerNewProjectStep.MyCustomization(course, onCreated));
  }

  protected static class MyCustomization extends PyCharmNewProjectStep.Customization {
    private final Course myCourse;
    private final PyStudyDirectoryProjectGenerator myGenerator;

    public MyCustomization(@NotNull Course course, @Nullable Consumer<Project> onCreated) {
      myCourse = course;
      myGenerator = new PyStudyDirectoryProjectGenerator(true, onCreated);
    }

    @NotNull
    @Override
    protected DirectoryProjectGenerator[] getProjectGenerators() {
      return new DirectoryProjectGenerator[] {};
    }

    @NotNull
    @Override
    protected DirectoryProjectGenerator createEmptyProjectGenerator() {
      StudyProjectGenerator generator = myGenerator.getGenerator();
      ArrayList<Course> courses = new ArrayList<>();
      courses.add(myCourse);
      generator.setCourses(courses);
      generator.setSelectedCourse(myCourse);
      return myGenerator;
    }
  }
}
