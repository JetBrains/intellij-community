package org.jetbrains.plugins.ruby.ruby.actions.setup;

import com.intellij.ide.actions.runAnything.RunAnythingAction;
import com.intellij.ide.actions.runAnything.activity.RunAnythingActivityProvider;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.impl.LaterInvocator;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class RubyConsoleActivityProvider implements RunAnythingActivityProvider {
  @NotNull
  protected abstract String getActionID();

  @Override
  public boolean runActivity(@NotNull DataContext dataContext, @NotNull String pattern) {
    AnAction action = ActionManager.getInstance().getAction(getActionID());

    AnActionEvent event = dataContext.getData(RunAnythingAction.RUN_ANYTHING_EVENT_KEY);
    ActionUtil.performDumbAwareUpdate(LaterInvocator.isInModalContext(), action, Objects.requireNonNull(event), false);

    if (!event.getPresentation().isEnabled()) {
      return false;
    }

    ActionManagerEx manager = ActionManagerEx.getInstanceEx();
    manager.fireBeforeActionPerformed(action, dataContext, event);
    ActionUtil.performActionDumbAware(action, event);
    manager.fireAfterActionPerformed(action, dataContext, event);

    return true;
  }
}