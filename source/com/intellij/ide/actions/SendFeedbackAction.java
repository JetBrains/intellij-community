/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 25.07.2006
 * Time: 14:26:00
 */
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.license.LicenseManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import org.jetbrains.annotations.NonNls;

public class SendFeedbackAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    launchBrowser();
  }

  public static void launchBrowser() {
    if (LicenseManager.getInstance().isEap()) {
      BrowserUtil.launchBrowser("http://jetbrains.net/jira");
    }
    else {
      @NonNls StringBuffer url = new StringBuffer("http://www.jetbrains.com/feedback/feedback.jsp?");
      url.append("product=");
      url.append(ApplicationNamesInfo.getInstance().getProductName());

      url.append("&build=");
      url.append(ApplicationInfo.getInstance().getBuildNumber());

      url.append("&timezone=");
      url.append(System.getProperty("user.timezone"));

      url.append("&eval=");
      url.append(LicenseManager.getInstance().isEvaluationLicense() ? "true" : "false");

      BrowserUtil.launchBrowser(url.toString());
    }
  }
}
