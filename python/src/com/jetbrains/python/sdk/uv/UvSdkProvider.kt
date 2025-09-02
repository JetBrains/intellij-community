// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PyInterpreterInspectionQuickFixData
import com.jetbrains.python.sdk.PySdkProvider
import org.jdom.Element
import javax.swing.Icon

class UvSdkProvider : PySdkProvider {
  override fun createEnvironmentAssociationFix(
    module: Module,
    sdk: Sdk,
    isPyCharm: Boolean,
    associatedModulePath: String?,
  ): PyInterpreterInspectionQuickFixData? {
    if (sdk.isUv) {
      val projectUnit = if (isPyCharm) "project" else "module"
      val message = when {
        associatedModulePath != null ->
          PyBundle.message("python.sdk.inspection.message.uv.interpreter.associated.with.another.project", projectUnit, associatedModulePath)
        else -> PyBundle.message("python.sdk.inspection.message.uv.interpreter.not.associated.with.any.project", projectUnit)
      }
      return PyInterpreterInspectionQuickFixData(UvAssociationQuickFix(), message)
    }

    return null
  }

  override fun getSdkAdditionalText(sdk: Sdk): String? = if (sdk.isUv) sdk.versionString else null

  override fun getSdkIcon(sdk: Sdk): Icon? {
    return if (sdk.isUv) UV_ICON else null
  }

  override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? {
    return UvSdkAdditionalData.load(element)
  }
}