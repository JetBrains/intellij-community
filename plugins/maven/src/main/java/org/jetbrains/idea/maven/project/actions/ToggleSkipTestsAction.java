package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.runner.MavenRunner;
import org.jetbrains.idea.maven.utils.actions.MavenToggleAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtils;

public class ToggleSkipTestsAction extends MavenToggleAction {
  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    return MavenRunner.getInstance(MavenActionUtils.getProject(e)).getState().isSkipTests();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state){
    MavenRunner.getInstance(MavenActionUtils.getProject(e)).getState().setSkipTests(state);
  }
}