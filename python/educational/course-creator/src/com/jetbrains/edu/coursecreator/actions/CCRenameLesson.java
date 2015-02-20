/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;

public class CCRenameLesson extends CCRename {

  public CCRenameLesson() {
    super("Rename Lesson", "Rename Lesson", null);
  }

  @Override
  public String getFolderName() {
    return EduNames.LESSON;
  }

  @Override
  public boolean processRename(Project project, PsiDirectory directory, Course course) {
    Lesson lesson = course.getLesson(directory.getName());
    if (lesson == null) {
      return false;
    }
    String newName = Messages.showInputDialog(project, "Enter new name", "Rename " + getFolderName(), null);
    if (newName == null) {
      return false;
    }
    lesson.setName(newName);
    return true;
  }
}
