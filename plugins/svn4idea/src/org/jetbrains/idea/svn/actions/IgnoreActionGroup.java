// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.ignore.FileGroupInfo;
import org.jetbrains.idea.svn.ignore.IgnoreGroupHelperAction;

import static org.jetbrains.idea.svn.SvnBundle.message;
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
  public void update(@NotNull final AnActionEvent e) {
    IgnoreGroupHelperAction helper = IgnoreGroupHelperAction.createFor(e);
    if (helper == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    FileGroupInfo fileGroupInfo = helper.getFileGroupInfo();

    removeAll();
    if (helper.allAreIgnored()) {
      if (helper.areIgnoreFilesOk()) {
        myRemoveExactAction
          .setActionText(fileGroupInfo.oneFileSelected() ? fileGroupInfo.getFileName() : message("action.Subversion.UndoIgnore.text"));
        add(myRemoveExactAction);
      }

      if (helper.areIgnoreExtensionOk()) {
        myRemoveExtensionAction.setActionText(fileGroupInfo.getExtensionMask());
        add(myRemoveExtensionAction);
      }

      e.getPresentation().setText(messagePointer("group.RevertIgnoreChoicesGroup.text"));
    }
    else if (helper.allCanBeIgnored()) {
      myAddExactAction.setActionText(
        fileGroupInfo.oneFileSelected() ? fileGroupInfo.getFileName() : message("action.Subversion.Ignore.ExactMatch.text"));
      add(myAddExactAction);
      if (fileGroupInfo.sameExtension()) {
        myAddExtensionAction.setActionText(fileGroupInfo.getExtensionMask());
        add(myAddExtensionAction);
      }
      e.getPresentation().setText(messagePointer("group.IgnoreChoicesGroup.text"));
    }
  }
}
