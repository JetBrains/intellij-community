// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.ignore.IgnoreGroupHelperAction;

import static org.jetbrains.idea.svn.SvnBundle.messagePointer;

public class IgnoreActionGroup extends DefaultActionGroup implements DumbAware {
  private final RemoveFromIgnoreListAction myRemoveExactAction;
  private final RemoveFromIgnoreListAction myRemoveExtensionAction;
  private final AddToIgnoreListAction myAddExactAction;
  private final AddToIgnoreListAction myAddExtensionAction;

  public IgnoreActionGroup() {
    myAddExactAction = new AddToIgnoreListAction(false);
    myAddExtensionAction = new AddToIgnoreListAction(true);
    myRemoveExactAction = new RemoveFromIgnoreListAction(false);
    myRemoveExtensionAction = new RemoveFromIgnoreListAction(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    IgnoreGroupHelperAction helper = IgnoreGroupHelperAction.createFor(e);
    if (helper == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    if (helper.allAreIgnored()) {
      presentation.setText(messagePointer("group.RevertIgnoreChoicesGroup.text"));
    }
    else if (helper.allCanBeIgnored()) {
      presentation.setText(messagePointer("group.IgnoreChoicesGroup.text"));
    }
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return new AnAction[]{
      myRemoveExactAction,
      myRemoveExtensionAction,
      myAddExactAction,
      myAddExtensionAction
    };
  }
}
