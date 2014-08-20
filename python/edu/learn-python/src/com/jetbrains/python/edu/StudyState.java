package com.jetbrains.python.edu;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.edu.course.Task;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.editor.StudyEditor;

public class StudyState {
  private final StudyEditor myStudyEditor;
  private final Editor myEditor;
  private final TaskFile myTaskFile;
  private final VirtualFile myVirtualFile;
  private final Task myTask;
  private final VirtualFile myTaskDir;

  public StudyState(final StudyEditor studyEditor) {
    myStudyEditor = studyEditor;
    myEditor = studyEditor != null ? studyEditor.getEditor() : null;
    myTaskFile = studyEditor != null ? studyEditor.getTaskFile() : null;
    myVirtualFile = myEditor != null ? FileDocumentManager.getInstance().getFile(myEditor.getDocument()) : null;
    myTaskDir = myVirtualFile != null ? myVirtualFile.getParent() : null;
    myTask = myTaskFile != null ? myTaskFile.getTask() : null;
  }

  public Editor getEditor() {
    return myEditor;
  }

  public TaskFile getTaskFile() {
    return myTaskFile;
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public Task getTask() {
    return myTask;
  }

  public VirtualFile getTaskDir() {
    return myTaskDir;
  }

  public boolean isValid() {
    return myStudyEditor != null && myEditor != null &&
           myTaskFile != null && myVirtualFile != null &&
           myTask != null && myTaskDir != null;
  }
}
