/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class AddDomElementActionGroup extends ActionGroup {

  private final AddElementInCollectionAction myAction = new AddElementInCollectionAction() {
    protected boolean showAsPopup() {
      return false;
    }
  };

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return myAction.getChildren(e);
  }

  public void update(AnActionEvent e) {
//    myAction.getChildren(e).length
    getTemplatePresentation().setText(myAction.getTemplatePresentation().getText());
    super.update(e);
  }
}
