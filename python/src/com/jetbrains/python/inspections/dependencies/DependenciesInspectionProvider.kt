// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.dependencies

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.packaging.PyRequirement
import org.jetbrains.annotations.ApiStatus

typealias DependenciesMap = Map<PyRequirement, PsiElement>

@ApiStatus.Internal
abstract class DependenciesInspectionProvider<T : PsiFile>(private val `class`: Class<T>) {
  protected abstract fun provideDependencies(file: T, sdk: Sdk): DependenciesMap?
  abstract val emptyFileInspectionMessage: @InspectionMessage String

  fun getDependencies(file: PsiElement, sdk: Sdk): DependenciesMap? =
    @Suppress("UNCHECKED_CAST")
    if (`class`.isInstance(file)) provideDependencies(file as T, sdk) else null
}

internal object DependenciesInspectionProviderData {
  private val EP_NAME =
    ExtensionPointName<DependenciesInspectionProvider<*>>(
      "com.jetbrains.python.inspections.dependencies.dependenciesInspectionProvider",
    )

  val providers: List<DependenciesInspectionProvider<*>>
    get() = EP_NAME.extensionList
}
