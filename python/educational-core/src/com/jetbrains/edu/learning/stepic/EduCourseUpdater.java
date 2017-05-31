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
package com.jetbrains.edu.learning.stepic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class EduCourseUpdater implements ApplicationComponent {

  private List<Course> ourRemoteCourses = new ArrayList<>();

  public List<Course> getRemoteCourses() {
    return ourRemoteCourses;
  }

  public static EduCourseUpdater getInstance() {
    return ApplicationManager.getApplication().getComponent(EduCourseUpdater.class);
  }

  public EduCourseUpdater() {
    ApplicationManager.getApplication().executeOnPooledThread(
      () -> ourRemoteCourses = new StudyProjectGenerator().getCourses(true));
  }

  @Override
  public void disposeComponent() {

  }

  @Override
  public void initComponent() {

  }

  @NotNull
  @Override
  public String getComponentName() {
    return "EduCourseUpdater";
  }
}
