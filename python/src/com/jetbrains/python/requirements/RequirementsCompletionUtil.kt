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
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.cache.firstPageOrEmpty
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.parser.icons.PythonParserIcons

private const val HYPHEN_DELIMITER = '-'
private const val UNDERSCORE_DELIMITER = '_'

fun completePackageNames(project: Project, interpreter: PythonInterpreter, result: CompletionResultSet) {
  val repositoryManager = PythonPackageManager.forPythonInterpreter(project, interpreter).repositoryManager
  val typedPrefix = result.prefixMatcher.prefix
  val normalizedPrefix = PyPackageName.normalizePackageName(typedPrefix)
  val packages =
    runBlockingCancellable {
      repositoryManager
        .searchPackages(normalizedPrefix)
        .values
        .flatMap { it.firstPageOrEmpty().successOrNull ?: emptyList() }
    }
  val displayDelimiter =
    if (typedPrefix.contains(UNDERSCORE_DELIMITER) && !typedPrefix.contains(HYPHEN_DELIMITER)) UNDERSCORE_DELIMITER
    else HYPHEN_DELIMITER

  // Collapses `-` / `_` to [delimiter], leaving every other character — notably `.` — untouched, so
  // `zope.interface` stays `zope.interface` on display and lookup; `PyPackageName.normalizePackageName`
  // would collapse it to `zope-interface`, which pip rejects.
  fun unifyDelimiter(name: String, delimiter: Char): String =
    name.map { if (it == HYPHEN_DELIMITER || it == UNDERSCORE_DELIMITER) delimiter else it }.joinToString("")

  result.restartCompletionOnAnyPrefixChange()
  val maxPriority = packages.size
  packages.asSequence().map {
    val canonical = it.lowercase()
    val display = unifyDelimiter(canonical, displayDelimiter)
    LookupElementBuilder.create(display)
      .withLookupString(unifyDelimiter(canonical, HYPHEN_DELIMITER))
      .withLookupString(unifyDelimiter(canonical, UNDERSCORE_DELIMITER))
      .withPresentableText(display)
      .withIcon(PythonParserIcons.PythonFile)
  }.mapIndexed { index, lookupElementBuilder ->
    PrioritizedLookupElement.withPriority(lookupElementBuilder, (maxPriority - index).toDouble())
  }.forEach { result.addElement(it) }
}

fun completeVersions(name: String, project: Project, interpreter: PythonInterpreter, result: CompletionResultSet, addQuotes: Boolean) {
  val packageManager = PythonPackageManager.forPythonInterpreter(project, interpreter)
  val repositoryManager = packageManager.repositoryManager

  val versions = ApplicationUtil.runWithCheckCanceled({
                                                        runBlockingCancellable {
                                                          repositoryManager.getVersions(
                                                            PyPackageName.normalizePackageName(name), null)
                                                          ?: emptyList()
                                                        }
                                                      }, EmptyProgressIndicator.notNullize(ProgressManager.getInstance().progressIndicator))

  versions.mapIndexed { index, version ->
    PrioritizedLookupElement.withPriority(LookupElementBuilder.create(if (addQuotes) "\"$version\"" else version), (versions.size - index).toDouble())
  }
    .forEach { result.addElement(it) }
}