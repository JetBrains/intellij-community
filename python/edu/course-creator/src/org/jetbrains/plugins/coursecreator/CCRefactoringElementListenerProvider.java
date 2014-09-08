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
package org.jetbrains.plugins.coursecreator;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.coursecreator.format.Course;
import org.jetbrains.plugins.coursecreator.format.Lesson;
import org.jetbrains.plugins.coursecreator.format.Task;
import org.jetbrains.plugins.coursecreator.format.TaskFile;

import java.util.Map;

public class CCRefactoringElementListenerProvider implements RefactoringElementListenerProvider {
  @Nullable
  @Override
  public RefactoringElementListener getListener(PsiElement element) {
    return new CCRenameListener(element);
  }


  static class CCRenameListener extends RefactoringElementAdapter {

    private String myElementName;

    public CCRenameListener(PsiElement element) {
      if (element instanceof PsiFile) {
        PsiFile psiFile = (PsiFile)element;
        myElementName = psiFile.getName();
      }
    }

    @Override
    protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
      if (newElement instanceof PsiFile && myElementName != null) {
        PsiFile psiFile = (PsiFile)newElement;
        if (myElementName.contains(".answer")) {
          //this is task file
          renameTaskFile(psiFile, myElementName);
        }
      }
    }

    private static void renameTaskFile(PsiFile file, String oldName) {
      PsiDirectory taskDir = file.getContainingDirectory();
      Course course = CCProjectService.getInstance(file.getProject()).getCourse();
      if (course == null) {
        return;
      }
      if (taskDir == null || !taskDir.getName().contains("task")) {
        return;
      }
      PsiDirectory lessonDir = taskDir.getParent();
      if (lessonDir == null || !lessonDir.getName().contains("lesson")) {
        return;
      }
      Lesson lesson = course.getLesson(lessonDir.getName());
      if (lesson == null) {
        return;
      }
      Task task = lesson.getTask(taskDir.getName());
      if (task == null) {
        return;
      }
      Map<String, TaskFile> taskFiles = task.getTaskFiles();
      TaskFile taskFile = task.getTaskFile(oldName);
      String realTaskFileName = CCProjectService.getRealTaskFileName(oldName);
      taskFiles.remove(realTaskFileName);
      taskFiles.put(CCProjectService.getRealTaskFileName(file.getName()), taskFile);
    }

    @Override
    public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {

    }
  }
}
