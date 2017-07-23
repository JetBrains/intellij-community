/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jetbrains.python;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.actions.SendFeedbackAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LicensingFacade;
import org.jetbrains.annotations.Nullable;

public class ReportProblemAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    launchBrowser(e.getProject());
  }

  public static void launchBrowser(@Nullable Project project) {
    final ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    boolean eap = appInfo.isEAP();
    String urlTemplate = appInfo.getEAPFeedbackUrl();
    urlTemplate = urlTemplate
      .replace("$BUILD", eap ? appInfo.getBuild().asStringWithoutProductCode() : appInfo.getBuild().asString())
      .replace("$TIMEZONE", System.getProperty("user.timezone"))
      .replace("$EVAL", isEvaluationLicense() ? "true" : "false")
      .replace("$DESCR", SendFeedbackAction.getDescription());
    BrowserUtil.browse(urlTemplate, project);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(ApplicationInfoEx.getInstanceEx() != null);
  }

  private static boolean isEvaluationLicense() {
    final LicensingFacade provider = LicensingFacade.getInstance();
    return provider != null && provider.isEvaluationLicense();
  }
}
