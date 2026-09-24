// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.codeInspection.InspectionEP
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.jetbrains.python.inspections.PySuppressionUtil
import kotlinx.coroutines.CoroutineScope

/**
 * Caches, for every registered inspection, its suppress id (the tool id passed to
 * [com.intellij.codeInspection.InspectionSuppressor]) and the reverse mapping from its industry-standard
 * kebab-case alias (e.g. `unresolved-references`) back to that id. [TypeIgnoreInspectionSuppressor] uses this
 * to tell a PyCharm inspection code such as `PyTypeChecker` / `unresolved-references` apart from a
 * foreign/mypy code such as `attr-defined` inside `# type: ignore[...]` / `# pycharm: ignore[...]`.
 */
@Service(Service.Level.APP)
internal class PyTypeIgnoreSuppressIds(scope: CoroutineScope) {
  private val index = SynchronizedClearableLazy(::computeIndex)

  init {
    val dropCache = Runnable { index.drop() }
    LocalInspectionEP.LOCAL_INSPECTION.addChangeListener(scope, dropCache)
    InspectionEP.GLOBAL_INSPECTION.addChangeListener(scope, dropCache)
  }

  fun isKnownSuppressId(suppressId: String): Boolean = suppressId in index.value.suppressIds

  /** Maps a kebab-case alias (e.g. `unresolved-references`) back to its suppress id, or `null` if unknown. */
  fun resolveKebabAlias(code: String): String? = index.value.kebabAliasToId[code]

  companion object {
    fun getInstance(): PyTypeIgnoreSuppressIds = service()
  }
}

private class SuppressIdIndex(@JvmField val suppressIds: Set<String>, @JvmField val kebabAliasToId: Map<String, String>)

private fun computeIndex(): SuppressIdIndex {
  val ids = HashSet<String>()
  val kebabAliasToId = HashMap<String, String>()
  fun register(id: String) {
    ids.add(id)
    PySuppressionUtil.toSuppressionCode(id)?.let { kebabAliasToId[it] = id }
  }
  for (ep in LocalInspectionEP.LOCAL_INSPECTION.extensionList) {
    register(ep.id ?: ep.shortName ?: continue)
  }
  for (ep in InspectionEP.GLOBAL_INSPECTION.extensionList) {
    register(ep.shortName ?: continue)
  }
  return SuppressIdIndex(ids, kebabAliasToId)
}
