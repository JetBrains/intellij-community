package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.actions.HelpTopicsAction;
import com.intellij.ui.UIBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;

/**
 * @author yole
 */
public class ReadHelpAction extends HelpTopicsAction {
  public ReadHelpAction() {
    getTemplatePresentation().setDescription(UIBundle.message("welcome.screen.read.help.action.description",
                                                              ApplicationNamesInfo.getInstance().getFullProductName()));
  }
}