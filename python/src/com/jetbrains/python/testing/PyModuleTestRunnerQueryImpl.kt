// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.util.ThreeState
import com.jetbrains.python.configuration.findModuleSdkIfReady
import com.jetbrains.python.testing.autoDetectTests.PyAutoDetectionConfigurationFactory

internal class PyModuleTestRunnerQueryImpl : PyModuleTestRunnerQuery {
  override fun isPyTestEnabled(module: Module): Boolean {
    val factory = TestRunnerService.getInstance(module).selectedFactory
    // Must be true even if SDK is not set
    if (factory.id == PyTestFactory.id) return true

    // Called from code insight, which cannot suspend, so it peeks at the snapshot.
    @Suppress("DEPRECATION") // The factory takes an SDK.
    val sdk = module.findEvoPyProjectIfReady()?.interpreter?.getSdkAPI() ?: return false
    val factoryId = (if (factory is PyAutoDetectionConfigurationFactory) factory.getFactory(sdk, module.project) else factory).id
    return factoryId == PyTestFactory.id
  }

  override fun isTestCaseClassRequired(anchor: PsiElement, explicit: ThreeState): Boolean {
    if (explicit != ThreeState.UNSURE) {
      return explicit.toBoolean()
    }
    val module = ModuleUtilCore.findModuleForPsiElement(anchor) ?: return true
    val sdk = findModuleSdkIfReady(module) ?: return true
    val factory = TestRunnerService.getInstance(module).selectedFactory
    return factory.onlyClassesAreSupported(module.project, sdk)
  }
}
