// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.python.requirements.RequirementsFile
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.python.requirements.parser.psi.RequirementsTypes

class RequirementsPackageNameCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.originalFile !is RequirementsFile)
      return
    val nameElement = parameters.position
    if ((nameElement as? LeafPsiElement)?.elementType != RequirementsTypes.PACKAGE_NAME_TOKEN)
      return

    val sdk = getPythonSdk(parameters.originalFile) ?: return

    completePackageNames(nameElement.project, sdk, result)
  }
}