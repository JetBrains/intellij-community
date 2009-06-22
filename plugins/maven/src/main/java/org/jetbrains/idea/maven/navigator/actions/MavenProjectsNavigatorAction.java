package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.utils.actions.MavenToggleAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtils;

public abstract class MavenProjectsNavigatorAction extends MavenToggleAction {
  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    return isSelected(getNavigator(e));
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    setSelected(getNavigator(e), state);
  }

  private MavenProjectsNavigator getNavigator(AnActionEvent e) {
    return MavenProjectsNavigator.getInstance(MavenActionUtils.getProject(e));
  }

  protected abstract boolean isSelected(MavenProjectsNavigator navigator);

  protected abstract void setSelected(MavenProjectsNavigator navigator, boolean value);
}
