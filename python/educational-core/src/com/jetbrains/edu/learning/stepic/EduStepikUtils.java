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

import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import org.jetbrains.annotations.Nullable;

public class EduStepikUtils {
  @Nullable
  public static String getLink(@Nullable Task task, int stepNumber) {
    if (task == null) {
      return null;
    }
    Lesson lesson = task.getLesson();
    if (lesson == null || !(lesson.getCourse() instanceof RemoteCourse)) {
      return null;
    }

    return String.format("%s/lesson/%d/step/%d", EduStepicNames.STEPIC_URL, lesson.getId(), stepNumber);
  }

  @Nullable
  public static String getAdaptiveLink(@Nullable Task task) {
    String link = getLink(task, 1);
    return link == null ? null : link + "?adaptive=true";
  }
}
