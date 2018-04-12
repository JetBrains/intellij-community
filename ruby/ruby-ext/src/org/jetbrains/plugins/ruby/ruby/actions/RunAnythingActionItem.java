package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

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
  public void run(@NotNull Project project, @NotNull DataContext dataContext) {
    Component focusOwner = dataContext.getData(RunAnythingAction.FOCUS_COMPONENT_KEY_NAME);
    AnActionEvent event = dataContext.getData(RunAnythingAction.RUN_ANYTHING_EVENT_KEY);
    RunAnythingUtil.performRunAnythingAction(myAction, project, focusOwner, event);
  }

  @NotNull
  @Override
  public String getText() {
    return myText;
  }

  @NotNull
  @Override
  public String getAdText() {
    return RunAnythingAction.AD_ACTION_TEXT;
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
  public Component createComponent(boolean isSelected) {
    return RunAnythingUtil.createActionCellRendererComponent(myAction, isSelected, myText);
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