// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin;

import com.intellij.openapi.project.Project;
import com.intellij.troubleshooting.TroubleInfoCollector;
import com.jetbrains.performancePlugin.utils.StatisticCollector;
import org.jetbrains.annotations.NotNull;

public class PerformanceTroubleInfoCollector implements TroubleInfoCollector {
  @Override
  public @NotNull String collectInfo(@NotNull Project project) {
    return "=====PERFORMANCE SUMMARY=====\n" + new StatisticCollector(project).collectMetrics(false);
  }

  @Override
  public String toString() {
    return "Performance";
  }
}
