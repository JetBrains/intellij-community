// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import org.jetbrains.idea.reposearch.SearchParameters

object MavenDependencySearchStatisticsCollector {
  private const val GROUP_ID = "build.maven.packagesearch"

  @JvmStatic
  fun notifyError(endPoint: String,
                  parameters: SearchParameters,
                  timeMillisToResponse: Long,
                  e: Throwable) {
    FUCounterUsageLogger.getInstance().logEvent(GROUP_ID,
                                                "packagesearch.error",
                                                FeatureUsageData()
                                                  .addData("time", timeMillisToResponse)
                                                  .addData("endpoint", endPoint)
                                                  .addData("useCache", parameters.useCache())
                                                  .addData("exception", e.javaClass.canonicalName));
  }

  @JvmStatic
  fun notifySuccess(endPoint: String,
                    parameters: SearchParameters,
                    timeMillisToResponse: Long) {
    FUCounterUsageLogger.getInstance().logEvent(GROUP_ID,
                                                "packagesearch.success",
                                                FeatureUsageData()
                                                  .addData("time", timeMillisToResponse)
                                                  .addData("endpoint", endPoint)
                                                  .addData("useCache", parameters.useCache()));
  }
}
