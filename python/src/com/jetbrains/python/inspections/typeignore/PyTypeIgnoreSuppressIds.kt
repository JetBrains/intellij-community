// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.codeInspection.InspectionEP
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.concurrency.SynchronizedClearableLazy
import kotlinx.coroutines.CoroutineScope

/**
 * Caches the suppress ids (the tool ids passed to [com.intellij.codeInspection.InspectionSuppressor])
 * of every registered inspection. [TypeIgnoreInspectionSuppressor] uses it to tell a PyCharm inspection
 * code such as `PyTypeChecker` apart from a foreign/mypy code such as `attr-defined` inside
 * `# type: ignore[...]`.
 */
@Service(Service.Level.APP)
internal class PyTypeIgnoreSuppressIds(scope: CoroutineScope) {
  private val suppressIds = SynchronizedClearableLazy(::computeSuppressIds)

  init {
    val dropCache = Runnable { suppressIds.drop() }
    LocalInspectionEP.LOCAL_INSPECTION.addChangeListener(scope, dropCache)
    InspectionEP.GLOBAL_INSPECTION.addChangeListener(scope, dropCache)
  }

  fun isKnownSuppressId(suppressId: String): Boolean = suppressId in suppressIds.value

  companion object {
    fun getInstance(): PyTypeIgnoreSuppressIds = service()
  }
}

private fun computeSuppressIds(): Set<String> {
  val result = HashSet<String>()
  for (ep in LocalInspectionEP.LOCAL_INSPECTION.extensionList) {
    result.add(ep.id ?: ep.shortName)
  }
  for (ep in InspectionEP.GLOBAL_INSPECTION.extensionList) {
    result.add(ep.shortName)
  }
  return result
}
