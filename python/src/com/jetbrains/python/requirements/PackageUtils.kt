// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.jetbrains.python.extensions.getSdk
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.PyPackageVersionComparator
import com.jetbrains.python.sdk.PythonSdkType

operator fun PyPackageVersion?.compareTo(other: PyPackageVersion?): Int {
  if (this == null && other == null) {
    return 0
  }
  if (this == null) {
    return -1
  }
  if (other == null) {
    return 1
  }
  val partsThis = this.release.split('.')
  val partsOther = other.release.split('.')
  val diff = partsThis.size - partsOther.size
  var a = this
  var b = other
  if (diff != 0) {
    val tail = Array(kotlin.math.abs(diff)) { "0" }
    if (diff > 0) {
      b = other.withRelease((partsOther + tail).joinToString("."))
    }
    else {
      a = this.withRelease((partsThis + tail).joinToString("."))
    }
  }
  return PyPackageVersionComparator.compare(a, b)
}

fun compareVersions(actual: PyPackageVersion?, operation: String, required: PyPackageVersion?): Boolean {
  if (operation == "===") {
    return actual?.presentableText == required?.presentableText
  }

  actual ?: return false
  required ?: return false

  if (operation == "==") {
    return actual.compareTo(required) == 0
  }

  if (operation == "~=") {
    val parts = required.release.split('.')
    if (parts.size < 2) {
      return compareVersions(actual, "==", required)
    }
    val partsAsInt = parts.subList(0, parts.size - 2).map { it.toInt() }
    val lastGroup = parts[parts.size - 2].toInt() + 1

    val maxRequired = required.withRelease((partsAsInt + listOf(lastGroup)).joinToString("."))
    return compareVersions(actual, ">=", required) && compareVersions(actual, "<", maxRequired)
  }

  if (operation == "!=") {
    return actual.compareTo(required) != 0
  }

  if (operation == ">=") {
    return compareVersions(actual, "==", required) || compareVersions(actual, ">", required)
  }

  if (operation == "<=") {
    return compareVersions(actual, "==", required) || compareVersions(actual, "<", required)
  }

  if (operation == ">") {
    if (actual.pre != null) {
      return false
    }
    return actual > required
  }
  if (operation == "<") {
    if (actual.pre != null) {
      return false
    }
    return actual < required
  }

  return false
}

fun getPythonSdk(psiFile: PsiFile): Sdk? {
  val virtualFile = psiFile.virtualFile ?: return null
  return getPythonSdk(psiFile.project, virtualFile)
}

fun getPythonSdk(project: Project, virtualFile: VirtualFile): Sdk? {
  val module = ModuleUtil.findModuleForFile(virtualFile, project) ?: return null
  val moduleSdk = module.getSdk() ?: return null
  if (moduleSdk.sdkType is PythonSdkType) {
    return moduleSdk
  }
  return null
}