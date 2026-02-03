// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule

abstract class MavenWhitelistRule(val myRuleId: String, resource: String) : CustomValidationRule() {
  private val whiteList: Set<String>

  init {
    val url = this::class.java.getResource(resource)
    whiteList = url
                  ?.readText()
                  ?.lines()
                  ?.asSequence()
                  ?.filter { it.isNotBlank() }
                  ?.map { it.trim() }
                  ?.filter { !it.startsWith('#') }
                  ?.toSet() ?: emptySet()

  }

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    return if (data in whiteList) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
  }

  override fun getRuleId(): String = myRuleId
}