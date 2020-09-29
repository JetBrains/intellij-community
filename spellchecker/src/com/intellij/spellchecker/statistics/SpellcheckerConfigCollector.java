// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.statistics;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.spellchecker.settings.SpellCheckerSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.internal.statistic.beans.MetricEventFactoryKt.*;

public final class SpellcheckerConfigCollector extends ProjectUsagesCollector {
  @Override
  public @NotNull String getGroupId() {
    return "spellchecker.settings.project";
  }

  @Override
  protected @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    SpellCheckerSettings settings = SpellCheckerSettings.getInstance(project);
    Set<MetricEvent> result = new HashSet<>(Arrays.asList(newMetric("all.bundled.enabled", true), newMetric("max.spellchecker.suggestions", 5),
                                  newCounterMetric("custom.dict.count", settings.getCustomDictionariesPaths().size()),
                                  newBooleanMetric("use.single.dict.to.save", settings.isUseSingleDictionaryToSave())));

    if (settings.isUseSingleDictionaryToSave()) {
      result.add(newMetric("default.dict.to.save", settings.getDictionaryToSave()));
    }
    return result;
  }
}
