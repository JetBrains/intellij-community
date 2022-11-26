// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class SpellcheckerActionStatistics : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("spellchecker.events", 2)
    @JvmField
    val REMOVE_FROM_ACCEPTED_WORDS = GROUP.registerEvent("remove.from.accepted.words.ui")
    @JvmField
    val ADD_TO_ACCEPTED_WORDS = GROUP.registerEvent("add.to.accepted.words.ui")
  }
}