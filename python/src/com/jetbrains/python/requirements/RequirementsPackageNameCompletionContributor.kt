// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.jetbrains.python.requirements.psi.SimpleName

class RequirementsPackageNameCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.position.parent !is SimpleName) return
    val sdk = getPythonSdk(parameters.originalFile) ?: return

    completePackageNames(parameters.position.project, sdk, result)
  }
}