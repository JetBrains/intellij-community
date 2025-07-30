// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stubs.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.packaging.common.NormalizedPythonPackageName

@Suppress("ActionIsNotPreviewFriendly")
internal class IgnoreStubAdvertiseQuickFix(val packageName: NormalizedPythonPackageName, val ignoredPackages: MutableList<String>) : LocalQuickFix {
  override fun getFamilyName() = PyPsiBundle.message("INSP.stub.packages.compatibility.ignore")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (ignoredPackages.add(packageName.name))
      ProjectInspectionProfileManager.getInstance(project).fireProfileChanged()
  }
}