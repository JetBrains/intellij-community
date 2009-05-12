package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.runner.MavenRunner;
import org.jetbrains.idea.maven.utils.MavenToggleAction;

public class ToggleSkipTestsAction extends MavenToggleAction {
  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    return MavenRunner.getInstance(getProject(e)).getState().isSkipTests();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state){
    MavenRunner.getInstance(getProject(e)).getState().setSkipTests(state);
  }
}