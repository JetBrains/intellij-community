// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml;

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl;
import com.intellij.ide.actions.searcheverywhere.ml.settings.SearchEverywhereMlSettings;
import com.intellij.internal.statistic.eventLog.EventLogConfiguration;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SearchEverywhereMlExperiment {
  private static final int NUMBER_OF_GROUPS = 4;

  private final boolean myIsExperimentalMode;
  private final Set<String> myTabsWithEnabledLogging;
  private final Map<SearchEverywhereTabWithMl, Experiment> myTabExperiments;

  public SearchEverywhereMlExperiment() {
    myIsExperimentalMode = StatisticsUploadAssistant.isSendAllowed() && ApplicationManager.getApplication().isEAP();
    myTabExperiments = createExperiments();
    myTabsWithEnabledLogging = ContainerUtil.newHashSet(
      SearchEverywhereTabWithMl.ACTION.getTabId(),
      SearchEverywhereTabWithMl.FILES.getTabId(),
      ClassSearchEverywhereContributor.class.getSimpleName(),
      SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
    );
  }

  private static HashMap<SearchEverywhereTabWithMl, Experiment> createExperiments() {
    return new HashMap<>(3) {{
      put(SearchEverywhereTabWithMl.ACTION,
          new Experiment()
            .addExperiment(ExperimentType.NO_ML, 1)
            .addExperiment(ExperimentType.USE_EXPERIMENTAL_MODEL, 2));

      put(SearchEverywhereTabWithMl.FILES,
          new Experiment().addExperiment(ExperimentType.USE_EXPERIMENTAL_MODEL, 3));
    }};
  }

  public boolean isAllowed() {
    final SearchEverywhereMlSettings settings = ApplicationManager.getApplication().getService(SearchEverywhereMlSettings.class);
    return settings.isSortingByMlEnabledInAnyTab() || !isDisableLoggingAndExperiments();
  }

  public boolean isLoggingEnabledForTab(@NotNull String tabId) {
    return myTabsWithEnabledLogging.contains(tabId);
  }

  @NotNull
  public ExperimentType getExperimentForTab(@NotNull SearchEverywhereTabWithMl tab) {
    if (isDisableLoggingAndExperiments() || isDisableExperiments(tab)) return ExperimentType.NO_EXPERIMENT;

    final Experiment tabExperiment = myTabExperiments.get(tab);
    if (tabExperiment == null) {
      return ExperimentType.NO_EXPERIMENT;
    }
    else {
      return tabExperiment.getExperimentByGroup(getExperimentGroup());
    }
  }

  private boolean isDisableLoggingAndExperiments() {
    return !myIsExperimentalMode || Registry.is("search.everywhere.force.disable.logging.ml");
  }

  private static boolean isDisableExperiments(@NotNull SearchEverywhereTabWithMl tab) {
    final String key = String.format("search.everywhere.force.disable.experiment.%s.ml", tab.name().toLowerCase(Locale.ROOT));
    return Registry.is(key);
  }

  public int getExperimentGroup() {
    if (!myIsExperimentalMode) {
      return -1;
    }
    else {
      int experimentGroup = EventLogConfiguration.getInstance().getBucket() % NUMBER_OF_GROUPS;
      int registryExperimentGroup = Registry.intValue("search.everywhere.ml.experiment.group");
      return registryExperimentGroup >= 0 ? registryExperimentGroup : experimentGroup;
    }
  }

  enum ExperimentType {
    NO_EXPERIMENT,
    NO_ML,
    USE_EXPERIMENTAL_MODEL
  }

  private static class Experiment {
    private final Map<Integer, ExperimentType> myExperiments = new HashMap<>(3);

    @NotNull
    Experiment addExperiment(@NotNull ExperimentType type, int group) {
      myExperiments.put(group, type);
      return this;
    }

    @NotNull
    public ExperimentType getExperimentByGroup(int group) {
      return myExperiments.getOrDefault(group, ExperimentType.NO_EXPERIMENT);
    }
  }
}
