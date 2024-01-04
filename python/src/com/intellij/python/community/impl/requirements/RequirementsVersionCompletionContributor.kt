// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.requirements

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.createSpecification
import com.jetbrains.python.sdk.pythonSdk

class RequirementsVersionCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val position = parameters.position
    val parent = position.parent
    val project = parameters.editor.project ?: return

    if (parent is com.intellij.python.community.impl.requirements.psi.VersionStmt) {
      val repositoryManager = PythonPackageManager.forSdk(project, project.pythonSdk ?: return).repositoryManager
      val name = PsiTreeUtil.getParentOfType(parent, com.intellij.python.community.impl.requirements.psi.NameReq::class.java)?.simpleName?.text ?: return
      val versions = ApplicationUtil.runWithCheckCanceled({
                                                            runBlockingCancellable {
                                                              repositoryManager.getPackageDetails(
                                                                repositoryManager.createSpecification(name, null, null)).availableVersions
                                                            }
                                                          }, ProgressManager.getInstance().progressIndicator)

      versions.mapIndexed { index, version ->
        PrioritizedLookupElement.withPriority(LookupElementBuilder.create(version), (versions.size - index).toDouble())
      }.forEach {
        result.addElement(it)
      }
    }
  }
}