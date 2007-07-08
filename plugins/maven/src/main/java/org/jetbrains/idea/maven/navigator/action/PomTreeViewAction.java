package org.jetbrains.idea.maven.navigator.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.navigator.MavenProjectNavigator;
import org.jetbrains.idea.maven.navigator.PomTreeViewSettings;

public abstract class PomTreeViewAction extends ToggleAction {
  public void update(final AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getNavigator(e) != null);
  }

  @Nullable
  private static MavenProjectNavigator getNavigator(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    return project == null ? null : MavenProjectNavigator.getInstance(project);
  }

  public boolean isSelected(AnActionEvent e) {
    final MavenProjectNavigator navigator = getNavigator(e);
    return navigator != null && isSelected(navigator.getTreeViewSettings());
  }

  public void setSelected(AnActionEvent e, boolean state) {
    final MavenProjectNavigator navigator = getNavigator(e);
    if (navigator != null) {
      setSelected(navigator.getTreeViewSettings(), state);
      navigator.updateFromRoot(true, isHard());
    }
  }

  boolean isHard() {
    return false;
  }

  protected abstract boolean isSelected(PomTreeViewSettings settings);

  protected abstract void setSelected(PomTreeViewSettings settings, boolean state);
}
