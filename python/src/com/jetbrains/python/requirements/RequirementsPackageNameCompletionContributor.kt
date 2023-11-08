package com.jetbrains.python.requirements

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.jetbrains.python.packaging.pip.PypiPackageCache
import com.jetbrains.python.requirements.psi.SimpleName
import icons.PythonIcons

class RequirementsPackageNameCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val position = parameters.position
    val parent = position.parent

    if (parent is SimpleName) {
      val cache = service<PypiPackageCache>()
      val maxPriority = cache.packages.size
      cache.packages.asSequence().map {
        LookupElementBuilder.create(it.lowercase()).withIcon(PythonIcons.Python.Python)
      }.mapIndexed { index, lookupElementBuilder ->
        PrioritizedLookupElement.withPriority(lookupElementBuilder, (maxPriority - index).toDouble())
      }.forEach { result.addElement(it) }
    }
  }
}