// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.freezes.promo

import com.intellij.diagnostic.FreezeNotifier
import com.intellij.diagnostic.LogMessage
import com.intellij.diagnostic.ThreadDump
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.Path

internal const val FREEZE_COUNT_KEY = "performance.plugin.promo.freeze.count"

internal class FreezeCounterListener : FreezeNotifier {
  override fun notifyFreeze(event: LogMessage, currentDumps: Collection<ThreadDump>, reportDir: Path, durationMs: Long) {
    val props = PropertiesComponent.getInstance()
    val currentCount = props.getInt(FREEZE_COUNT_KEY, 0)
    if (currentCount > FREEZE_THRESHOLD) {
      thisLogger().debug("Freeze count exceeded threshold, do not count further")
      return
    }

    props.setValue(FREEZE_COUNT_KEY, currentCount + 1, 0)
    thisLogger().debug("Freeze detected, incrementing freeze count for promo")
  }
}
