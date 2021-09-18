// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml;

import com.intellij.ide.actions.searcheverywhere.ml.settings.SearchEverywhereMlSettings;
import com.intellij.internal.statistic.eventLog.EventLogConfiguration;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class SearchEverywhereMlExperiment {
  private static final int NUMBER_OF_GROUPS = 4;

  private final int myExperimentGroup;
  private final boolean myIsExperimentalMode;

  public SearchEverywhereMlExperiment() {
    myIsExperimentalMode = StatisticsUploadAssistant.isSendAllowed() && ApplicationManager.getApplication().isEAP();
    myExperimentGroup = myIsExperimentalMode ? EventLogConfiguration.getInstance().getBucket() % NUMBER_OF_GROUPS : -1;
  }

  public boolean isAllowed() {
    final SearchEverywhereMlSettings settings = ApplicationManager.getApplication().getService(SearchEverywhereMlSettings.class);
    return settings.isSortingByMlEnabledInAnyTab() || !isDisableLoggingAndExperiments();
  }

  public boolean shouldPerformExperiment(@NotNull SearchEverywhereTabWithMl tab) {
    if (isDisableLoggingAndExperiments() || isDisableExperiments(tab)) return false;

    final int tabExperimentGroup = getExperimentGroupForTab(tab);
    return myExperimentGroup == tabExperimentGroup;
  }

  private boolean isDisableLoggingAndExperiments() {
    return !myIsExperimentalMode || Registry.is("search.everywhere.force.disable.logging.ml");
  }

  private static boolean isDisableExperiments(@NotNull SearchEverywhereTabWithMl tab) {
    final String key = String.format("search.everywhere.force.disable.experiment.%s.ml", tab.name().toLowerCase(Locale.ROOT));
    return Registry.is(key);
  }

  private static int getExperimentGroupForTab(@NotNull SearchEverywhereTabWithMl tab) {
    if (tab == SearchEverywhereTabWithMl.ACTION) {
      return 1;
    }

    return -1;
  }

  public int getExperimentGroup() {
    return myExperimentGroup;
  }
}
