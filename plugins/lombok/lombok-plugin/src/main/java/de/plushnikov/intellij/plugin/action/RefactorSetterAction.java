package de.plushnikov.intellij.plugin.action;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;

public class RefactorSetterAction extends LombokRefactorAction {

  protected RefactorSetterHandler initHandler(Project project, DataContext dataContext) {
    return new RefactorSetterHandler(project, dataContext);
  }
}