package com.intellij.debugger.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 14, 2004
 * Time: 11:15:59 AM
 * To change this template use File | Settings | File Templates.
 */
public class EditFrameSourceAction extends GotoFrameSourceAction{
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setText(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getTemplatePresentation().getText());
  }
}
