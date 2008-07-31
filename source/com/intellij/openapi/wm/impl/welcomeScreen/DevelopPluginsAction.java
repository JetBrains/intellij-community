package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author yole
 */
public class DevelopPluginsAction extends AnAction {
  @NonNls private static final String PLUGIN_URL = PathManager.getHomePath() + "/Plugin Development Readme.html";
  @NonNls private static final String PLUGIN_WEBSITE = "http://www.jetbrains.com/idea/plugins/plugin_developers.html";

  public DevelopPluginsAction() {
    getTemplatePresentation().setDescription(UIBundle.message("welcome.screen.plugin.development.action.description", 
                                             ApplicationNamesInfo.getInstance().getFullProductName()));
  }

  public void actionPerformed(final AnActionEvent e) {
    try {
      if (new File(PLUGIN_URL).isFile()) {
        BrowserUtil.launchBrowser(PLUGIN_URL);
      }
      else {
        BrowserUtil.launchBrowser(PLUGIN_WEBSITE);
      }
    }
    catch(IllegalStateException ex) {
      // ignore
    }
  }
}