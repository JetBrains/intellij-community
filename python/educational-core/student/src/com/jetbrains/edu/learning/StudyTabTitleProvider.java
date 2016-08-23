package com.jetbrains.edu.learning;

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.fileEditor.impl.UniqueNameEditorTabTitleProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class StudyTabTitleProvider implements EditorTabTitleProvider {
  @Nullable
  @Override
  public String getEditorTabTitle(Project project, VirtualFile file) {
    TaskFile taskFile = StudyUtils.getTaskFile(project, file);
    if (taskFile == null) {
      return null;
    }
    String title = new UniqueNameEditorTabTitleProvider().getEditorTabTitle(project, file);
    if (title == null) {
      return null;
    }
    String[] split = title.split(File.separator);
    for (int i = 0; i < split.length; i++) {
      String part = split[i];
      Task task = taskFile.getTask();
      VirtualFile taskDir = task.getTaskDir(project);
      if (taskDir != null && part.equals(taskDir.getName())) {
        split[i] = task.getName();
        continue;
      }
      Lesson lesson = task.getLesson();
      int lessonIndex = lesson.getIndex();
      if (part.equals(EduNames.LESSON + lessonIndex)) {
        split[i] = lesson.getName();
      }
    }
    return StringUtil.join(split, File.separator);
  }
}
