// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.statistics;

import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;

public final class ShFeatureUsagesCollector {
  public static void logFeatureUsage(String eventId) {
    FUCounterUsageLogger.getInstance().logEvent("sh.shell.script", eventId);
  }
}