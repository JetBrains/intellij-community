/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.edu.learning.statistics;

import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class EduUsagesCollector extends AbstractApplicationUsagesCollector {
  private static final String GROUP_ID = "educational-course";

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException {
    final Set<UsageDescriptor> result = new HashSet<UsageDescriptor>();

    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course != null) {
      if (course.isAdaptive()) {
        result.add(new UsageDescriptor("Adaptive", 1));
      }
      else if (EduNames.STUDY.equals(course.getCourseMode())) {
        result.add(new UsageDescriptor(EduNames.STUDY, 1));
      }
      else {
        result.add(new UsageDescriptor("Course Creator", 1));
      }
    }
    return result;
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID);
  }
}
