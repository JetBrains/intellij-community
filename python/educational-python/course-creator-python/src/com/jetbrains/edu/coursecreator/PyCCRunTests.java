package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.coursecreator.actions.PyCCRunTestsAction;
import com.jetbrains.python.edu.PyExecuteFileExtensionPoint;
import org.jetbrains.annotations.NotNull;

public class PyCCRunTests implements PyExecuteFileExtensionPoint {

  @NotNull
  public AnAction getRunAction() {
    return new PyCCRunTestsAction();
  }

  @Override
  public boolean accept(Project project) {
    return CCProjectService.getInstance(project).getCourse() != null;
  }
}