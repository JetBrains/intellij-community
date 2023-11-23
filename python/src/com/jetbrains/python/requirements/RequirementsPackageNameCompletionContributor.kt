package com.jetbrains.python.requirements

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.requirements.psi.SimpleName
import com.jetbrains.python.sdk.pythonSdk
import icons.PythonIcons

class RequirementsPackageNameCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val position = parameters.position
    val parent = position.parent
    val project = position.project

    if (parent is SimpleName) {
      val repositoryManager = PythonPackageManager.forSdk(project, project.pythonSdk ?: return).repositoryManager
      val packages = repositoryManager.allPackages()
      val maxPriority = packages.size
      packages.asSequence().map {
        LookupElementBuilder.create(it.lowercase()).withIcon(PythonIcons.Python.Python)
      }.mapIndexed { index, lookupElementBuilder ->
        PrioritizedLookupElement.withPriority(lookupElementBuilder, (maxPriority - index).toDouble())
      }.forEach { result.addElement(it) }
    }
  }
}