package org.jetbrains.idea.maven.navigator.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigatorSettings;
import org.jetbrains.idea.maven.utils.MavenToggleAction;

public abstract class MavenProjectsNavigatorAction extends MavenToggleAction {
  public void update(final AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getNavigator(e) != null);
  }

  @Nullable
  private static MavenProjectsNavigator getNavigator(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    return project == null ? null : MavenProjectsNavigator.getInstance(project);
  }

  public boolean isSelected(AnActionEvent e) {
    MavenProjectsNavigator navigator = getNavigator(e);
    return navigator != null && isSelected(navigator.getTreeViewSettings());
  }

  public void setSelected(AnActionEvent e, boolean state) {
    MavenProjectsNavigator navigator = getNavigator(e);
    if (navigator != null) {
      setSelected(navigator.getTreeViewSettings(), state);
      navigator.updateFromRoot(true, true);
    }
  }

  protected abstract boolean isSelected(MavenProjectsNavigatorSettings settings);

  protected abstract void setSelected(MavenProjectsNavigatorSettings settings, boolean state);
}
