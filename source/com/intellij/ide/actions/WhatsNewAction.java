package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * @author max
 */
public class WhatsNewAction extends AnAction{
  public void actionPerformed(AnActionEvent e) {
    BrowserUtil.launchBrowser("http://www.jetbrains.com/idea/features/newfeatures.html");
  }
}
