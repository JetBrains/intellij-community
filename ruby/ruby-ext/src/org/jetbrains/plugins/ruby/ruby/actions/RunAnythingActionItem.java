package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.execution.Executor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class RunAnythingActionItem extends RunAnythingItem<AnAction> {
  @NotNull private final AnAction myAction;
  @NotNull private final String myText;

  public RunAnythingActionItem(@NotNull AnAction action, @NotNull String text) {
    myAction = action;
    myText = text;
  }

  @Override
  public void runInner(@NotNull Executor executor,
                       @Nullable VirtualFile workDirectory,
                       @Nullable Component component,
                       @NotNull Project project,
                       @Nullable AnActionEvent event) {
    RunAnythingUtil.performRunAnythingAction(myAction, project, component, event);
  }

  @NotNull
  @Override
  public String getText() {
    return myText;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return ObjectUtils.notNull(myAction.getTemplatePresentation().getIcon(), AllIcons.Toolwindows.ToolWindowRun);
  }

  @NotNull
  @Override
  public AnAction getValue() {
    return myAction;
  }

  @NotNull
  @Override
  public Component getComponent(boolean isSelected) {
    return RunAnythingUtil.getActionCellRendererComponent(myAction, isSelected, myText);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RunAnythingActionItem item = (RunAnythingActionItem)o;
    return Objects.equals(myAction, item.myAction) &&
           Objects.equals(myText, item.myText);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myAction, myText);
  }
}