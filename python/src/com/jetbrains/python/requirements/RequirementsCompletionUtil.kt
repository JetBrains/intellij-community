// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.createSpecification
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import com.jetbrains.python.sdk.pythonSdk

fun completePackageNames(project: Project, result: CompletionResultSet) {
  val repositoryManager = PythonPackageManager.forSdk(project, project.pythonSdk ?: return).repositoryManager
  val packages = repositoryManager.allPackages()
  val maxPriority = packages.size
  packages.asSequence().map {
    LookupElementBuilder.create(it.lowercase()).withIcon(PythonPsiApiIcons.Python)
  }.mapIndexed { index, lookupElementBuilder ->
    PrioritizedLookupElement.withPriority(lookupElementBuilder, (maxPriority - index).toDouble())
  }.forEach { result.addElement(it) }
}

fun completeVersions(name: String, project: Project, result: CompletionResultSet, addQuotes: Boolean) {
  val repositoryManager = PythonPackageManager.forSdk(project, project.pythonSdk ?: return).repositoryManager
  val packageSpecification = repositoryManager.createSpecification(name, null, null) ?: return
  val versions = ApplicationUtil.runWithCheckCanceled({
                                                        runBlockingCancellable {
                                                          repositoryManager.getPackageDetails(packageSpecification).availableVersions
                                                        }
                                                      }, EmptyProgressIndicator.notNullize(ProgressManager.getInstance().progressIndicator))

  versions.mapIndexed { index, version ->
    PrioritizedLookupElement.withPriority(LookupElementBuilder.create(if (addQuotes) "\"$version\"" else version), (versions.size - index).toDouble())
  }
    .forEach { result.addElement(it) }
}