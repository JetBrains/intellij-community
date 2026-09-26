// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.dependencies

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.packaging.PyRequirement
import org.jetbrains.annotations.ApiStatus

typealias DependencyMap = Map<PyRequirement, PsiElement>

@ApiStatus.Internal
abstract class DependenciesPsiProvider<T : PsiFile>(
  internal val `class`: Class<T>,
  internal val language: Language,
) {
  protected abstract fun provideDependencies(file: T): DependencyMap?
  abstract val emptyFileInspectionMessage: @InspectionMessage String?

  internal fun getDependencies(file: PsiFile): DependencyMap? =
    @Suppress("UNCHECKED_CAST")
    if (`class`.isInstance(file)) provideDependencies(file as T) else null
}

internal object DependenciesPsiProviderData {
  private val EP_NAME =
    ExtensionPointName<DependenciesPsiProvider<*>>(
      "com.jetbrains.python.inspections.dependencies.dependenciesInspectionProvider",
    )

  /**
   * @return a map of [DependenciesPsiProvider] to [DependencyMap] for all eligible providers, or null if none were found.
   */
  fun dependenciesForFile(file: PsiFile): Map<DependenciesPsiProvider<*>, DependencyMap>? =
    EP_NAME
      .extensionList
      .mapNotNull { provider ->
        provider
          .getDependencies(file)
          ?.let { provider to it }
      }
      .toMap()
      .takeIf { it.isNotEmpty() }


  val classes: Set<Class<*>>
    get() =
      EP_NAME
        .extensionList
        .map { it.`class` }
        .toSet()

  val languages: Set<Language>
    get() =
      EP_NAME
        .extensionList
        .map { it.language }
        .toSet()
}
