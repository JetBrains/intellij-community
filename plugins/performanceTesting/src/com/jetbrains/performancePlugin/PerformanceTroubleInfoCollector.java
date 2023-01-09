package com.jetbrains.performancePlugin;

import com.intellij.openapi.project.Project;
import com.intellij.troubleshooting.TroubleInfoCollector;
import com.jetbrains.performancePlugin.utils.StatisticCollector;
import org.jetbrains.annotations.NotNull;

public class PerformanceTroubleInfoCollector implements TroubleInfoCollector {
  @NotNull
  @Override
  public String collectInfo(@NotNull Project project) {
    return "=====PERFORMANCE SUMMARY=====\n" + new StatisticCollector(project).collectMetrics(false);
  }

  @Override
  public String toString() {
    return "Performance";
  }
}
