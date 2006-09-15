package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

public abstract class AutoScrollFromSourceHandler {
  protected final Project myProject;

  protected AutoScrollFromSourceHandler(Project project) {
    myProject = project;
  }

  protected abstract boolean isAutoScrollMode();
  protected abstract void setAutoScrollMode(boolean state);
  public abstract void install();
  public abstract void dispose();

  public ToggleAction createToggleAction() {
    return new ToggleAction(UIBundle.message("autoscroll.from.source.action.name"),
                            UIBundle.message("autoscroll.from.source.action.description"), IconLoader.getIcon("/general/autoscrollFromSource.png")) {
      public boolean isSelected(AnActionEvent event) {
        return isAutoScrollMode();
      }

      public void setSelected(AnActionEvent event, boolean flag) {
        setAutoScrollMode(flag);
      }
    };
  }
}

