package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;

public class CCAddAsTaskFile extends CCTaskFileActionBase {
  public static final String ACTION_NAME = "Make Visible to Student";

  public CCAddAsTaskFile() {
    super(ACTION_NAME);
  }


  protected void performAction(VirtualFile file, Task task, Course course, Project project) {
    EduUtils.runUndoableAction(project, ACTION_NAME, new AddTaskFile(file, null, project, task));
  }

  protected boolean isAvailable(Project project, VirtualFile file) {
    return StudyUtils.getTaskFile(project, file) == null && !CCUtils.isTestsFile(project, file);
  }

  private static class AddTaskFile extends BasicUndoableAction {
    private final VirtualFile myFile;
    private TaskFile myTaskFile;
    private final Project myProject;
    private final Task myTask;

    public AddTaskFile(VirtualFile file, TaskFile taskFile, Project project, Task task) {
      super(file);
      myFile = file;
      myTaskFile = taskFile;
      myProject = project;
      myTask = task;
    }

    @Override
    public void undo() throws UnexpectedUndoException {
      if (myTaskFile == null) return;
      CCHideFromStudent.hideFromStudent(myFile, myProject, myTask.getTaskFiles(), myTaskFile);
      ProjectView.getInstance(myProject).refresh();
    }

    @Override
    public void redo() throws UnexpectedUndoException {
      if (myTaskFile != null) {
        myTask.addTaskFile(myTaskFile);
      } else {
        final String taskRelativePath = FileUtil.getRelativePath(myTask.getTaskDir(myProject).getPath(), myFile.getPath(), '/');
        myTask.addTaskFile(taskRelativePath, myTask.getTaskFiles().size());
        myTaskFile = myTask.getTaskFile(taskRelativePath);
      }
      ProjectView.getInstance(myProject).refresh();
    }

    @Override
    public boolean isGlobal() {
      return true;
    }
  }
}
