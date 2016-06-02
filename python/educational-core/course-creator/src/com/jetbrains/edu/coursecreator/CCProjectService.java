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
package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.jetbrains.edu.learning.courseFormat.Course;
import org.jetbrains.annotations.NotNull;

@State(name = "CCProjectService", storages = @Storage("course_service.xml"))
public class CCProjectService implements PersistentStateComponent<CCProjectService> {
  private Course myCourse;

  public Course getCourse() {
    return myCourse;
  }

  public void setCourse(Course course) {
    myCourse = course;
  }

  @Override
  public CCProjectService getState() {
    return this;
  }

  @Override
  public void loadState(CCProjectService state) {
    XmlSerializerUtil.copyBean(state, this);
    myCourse.initCourse(true);
  }

  public static CCProjectService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, CCProjectService.class);
  }
}
