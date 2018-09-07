// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.tree.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class AddDomElementActionGroup extends ActionGroup {
  private AddElementInCollectionAction myAction;

  @NotNull
  private static AddElementInCollectionAction createChildAction() {
    return new AddElementInCollectionAction() {
      @Override
      protected boolean showAsPopup() {
        return false;
      }
    };
  }

  @Override
  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (myAction == null) {
      myAction = createChildAction();
    }
    return myAction.getChildren(e);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (myAction == null) {
      myAction = createChildAction();
    }

    getTemplatePresentation().setText(myAction.getTemplatePresentation().getText());
    super.update(e);
  }
}
