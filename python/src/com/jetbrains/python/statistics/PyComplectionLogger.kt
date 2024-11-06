// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.codeInsight.lookup.impl.LookupResultDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageTracker
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import com.intellij.util.SlowOperations
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.resolve.QualifiedNameFinder

class PyCompletionStatisticLogger : LookupUsageDescriptor {
  override fun getExtensionKey(): String = "python_package"

  override fun getAdditionalUsageData(lookupResultDescriptor: LookupResultDescriptor): List<EventPair<*>> {
    return lookupResultDescriptor.selectedItem?.let {
      val psiElement = it.psiElement
      psiElement?.containingFile?.let { file ->
        if (file is PyFile && file.virtualFile != null) {
          val qName = SlowOperations.knownIssue("PY-70370, EA-928705").use {
            QualifiedNameFinder.findCachedShortestImportableName(file, file.virtualFile)
          }
          qName?.firstComponent?.let { name ->
            listOf(packageName.with(PyPsiPackageUtil.moduleToPackageName(name)),
                   cacheMiss.with(false))
          } ?: listOf(cacheMiss.with(true))
        } else null
      }
    } ?: emptyList()
  }
}

private val packageName by lazy { EventFields.StringValidatedByEnum("py_package_name", "python_packages") }
private val cacheMiss by lazy { EventFields.Boolean("py_cache_miss") }

class PyCompletionUsageExtension : FeatureUsageCollectorExtension {
  override fun getGroupId(): String {
    return LookupUsageTracker.GROUP_ID
  }

  override fun getEventId(): String {
    return LookupUsageTracker.FINISHED_EVENT_ID
  }

  override fun getExtensionFields(): List<EventField<*>> {
    return listOf(packageName, cacheMiss)
  }
}