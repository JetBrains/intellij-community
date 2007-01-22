/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 22-Jan-2007
 */
package com.intellij.analysis;

import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.UIOptions;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.project.Project;

public class PerformAnalysisInBackgroundOption implements PerformInBackgroundOption {
  private UIOptions myUIOptions;

  public PerformAnalysisInBackgroundOption(Project project) {
    myUIOptions = ((InspectionManagerEx)InspectionManagerEx.getInstance(project)).getUIOptions();
  }

  public boolean shouldStartInBackground() {
    return myUIOptions.ANALYSIS_IN_BACKGROUND;
  }

  public void processSentToBackground() {
    myUIOptions.ANALYSIS_IN_BACKGROUND = true;
  }

  public void processRestoredToForeground() {
    myUIOptions.ANALYSIS_IN_BACKGROUND = false;
  }
}