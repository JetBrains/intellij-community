// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.codeVision

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction

internal object PyCodeVisionUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("python.code.vision", 1)

  private const val CLASS_LOCATION = "class"
  private const val FUNCTION_LOCATION = "function"
  private const val METHOD_LOCATION = "method"
  private const val UNKNOWN_LOCATION = "unknown"
  private val LOCATION_FIELD: StringEventField = EventFields.String("location", listOf(
    CLASS_LOCATION, FUNCTION_LOCATION, METHOD_LOCATION, UNKNOWN_LOCATION
  ))

  private val USAGES_CLICKED_EVENT_ID = GROUP.registerEvent("usages.clicked", LOCATION_FIELD)

  private fun getLocation(element: PsiElement): String = when (element) {
    is PyClass -> CLASS_LOCATION
    is PyFunction -> if (element.asMethod() != null) METHOD_LOCATION else FUNCTION_LOCATION
    else -> UNKNOWN_LOCATION
  }

  fun logClickToFUS(element: PsiElement) {
    val location = getLocation(element)
    USAGES_CLICKED_EVENT_ID.log(element.project, location)
  }

  override fun getGroup(): EventLogGroup = GROUP
}