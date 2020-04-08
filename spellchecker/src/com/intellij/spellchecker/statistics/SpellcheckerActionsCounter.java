// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.statistics;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import org.jetbrains.annotations.NotNull;

public class SpellcheckerActionsCounter {

  public static void quickFixApplied(@NotNull String fix, boolean canceled){
    FeatureUsageData data = new FeatureUsageData().addData("canceled", canceled);
    FUCounterUsageLogger.getInstance().logEvent("spellchecker.fixes", fix, data);
  }
}
