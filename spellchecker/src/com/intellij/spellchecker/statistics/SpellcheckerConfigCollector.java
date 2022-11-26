// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.statistics;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.spellchecker.settings.SpellCheckerSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public final class SpellcheckerConfigCollector extends ProjectUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("spellchecker.settings.project", 2);
  private static final EventId1<Boolean> ALL_BUNDLED_ENABLED = GROUP.registerEvent("all.bundled.enabled",
                                                                                   EventFields.Boolean("value"));
  private static final EventId1<Integer> MAX_SPELLCHECKER_SUGGESTIONS = GROUP.registerEvent("max.spellchecker.suggestions",
                                                                                            EventFields.Int("value"));
  private static final EventId1<Integer> CUSTOM_DIC_COUNT = GROUP.registerEvent("custom.dict.count",
                                                                                EventFields.Count);
  private static final EventId1<Boolean> USE_SINGLE_DICT_TO_SAVE = GROUP.registerEvent("use.single.dict.to.save", EventFields.Enabled);
  private static final EventId1<String> DEFAULT_DICT_TO_SAVE = GROUP.registerEvent("default.dict.to.save",
                                                                                   EventFields.String("value", List.of("project-level",
                                                                                                                       "application-level")));

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  protected @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    SpellCheckerSettings settings = SpellCheckerSettings.getInstance(project);
    Set<MetricEvent> result =
      ContainerUtil.set(ALL_BUNDLED_ENABLED.metric(true),
                        MAX_SPELLCHECKER_SUGGESTIONS.metric(5),
                        CUSTOM_DIC_COUNT.metric(settings.getCustomDictionariesPaths().size()),
                        USE_SINGLE_DICT_TO_SAVE.metric(settings.isUseSingleDictionaryToSave()));

    if (settings.isUseSingleDictionaryToSave()) {
      result.add(DEFAULT_DICT_TO_SAVE.metric(settings.getDictionaryToSave()));
    }
    return result;
  }
}
