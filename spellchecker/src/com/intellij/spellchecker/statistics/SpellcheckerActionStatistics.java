// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.statistics;

import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import org.jetbrains.annotations.NotNull;

public class SpellcheckerActionStatistics {

  public static void reportAction(@NotNull String action) {
    FUCounterUsageLogger.getInstance().logEvent("spellchecker.events", action);
  }
}
