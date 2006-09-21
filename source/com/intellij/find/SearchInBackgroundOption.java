/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.find;

import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.progress.PerformInBackgroundOption;

/**
 * @author max
 */
public class SearchInBackgroundOption implements PerformInBackgroundOption {
  public boolean shouldStartInBackground() {
    return GeneralSettings.getInstance().isSearchInBackground();
  }

  public void processSentToBackground() {
    GeneralSettings.getInstance().setSearchInBackground(true);
  }

  public void processRestoredToForeground() {
    GeneralSettings.getInstance().setSearchInBackground(false);
  }
}
