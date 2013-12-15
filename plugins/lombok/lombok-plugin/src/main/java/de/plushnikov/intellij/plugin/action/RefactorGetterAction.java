package de.plushnikov.intellij.plugin.action;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;

public class RefactorGetterAction extends LombokRefactorAction {

  protected RefactorGetterHandler initHandler(Project project, DataContext dataContext) {
    return new RefactorGetterHandler(project, dataContext);
  }
}