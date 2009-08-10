/*
 * User: anna
 * Date: 15-May-2008
 */
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.util.ParameterTablePanel;

public abstract class AbstractExtractDialog extends DialogWrapper {
  protected AbstractExtractDialog(Project project) {
    super(project, true);
  }


  public abstract String getChosenMethodName();

  public abstract ParameterTablePanel.VariableData[] getChosenParameters();

  public abstract String getVisibility();

  public abstract boolean isMakeStatic();

  public abstract boolean isChainedConstructor();
}