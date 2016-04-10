package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.DocumentUtil;
import com.jetbrains.edu.learning.StudyActionListener;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;

import java.util.Map;

public class CCStudyActionListener implements StudyActionListener {
  @Override
  public void beforeCheck(AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) {
      return;
    }
    VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(event.getDataContext());
    if (virtualFile == null) {
      return;
    }

    TaskFile taskFile = StudyUtils.getTaskFile(project, virtualFile);
    if (taskFile == null) {
      return;
    }

    Task task = taskFile.getTask();
    VirtualFile taskDir = StudyUtils.getTaskDir(virtualFile);
    if (taskDir == null) {
      return;
    }
    Map<String, TaskFile> files = task.getTaskFiles();
    for (Map.Entry<String, TaskFile> entry : files.entrySet()) {
      String name = entry.getKey();
      VirtualFile child = taskDir.findChild(name);
      if (child == null) {
        continue;
      }
      Document patternDocument = StudyUtils.getPatternDocument(entry.getValue(), name);
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      if (document == null || patternDocument == null) {
        return;
      }
      DocumentUtil.writeInRunUndoTransparentAction(() -> {
        patternDocument.replaceString(0, patternDocument.getTextLength(), document.getCharsSequence());
        FileDocumentManager.getInstance().saveDocument(patternDocument);
      });
      TaskFile target = new TaskFile();
      TaskFile.copy(taskFile, target);
      for (AnswerPlaceholder placeholder : target.getAnswerPlaceholders()) {
        placeholder.setUseLength(false);
      }
      EduUtils.createStudentDocument(project, target, child, patternDocument);
    }
  }
}
