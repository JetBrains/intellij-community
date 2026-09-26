// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stubs.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.installPyRequirementsBackground
import com.jetbrains.python.packaging.utils.PyPackageCoroutine

@Suppress("ActionIsNotPreviewFriendly")
internal class InstallStubQuickFix(private val stub: PyRequirement, private val sdk: Sdk) : LocalQuickFix {
  override fun getFamilyName() = PyPsiBundle.message("INSP.stub.packages.compatibility.install")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    PyPackageCoroutine.launch(project) {
      PythonPackageManagerUI.forSdk(project, sdk).installPyRequirementsBackground(listOf(stub))
    }
  }
}