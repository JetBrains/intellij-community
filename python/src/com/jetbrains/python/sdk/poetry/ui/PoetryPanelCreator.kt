// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry.ui

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.UserDataHolder
import com.intellij.util.PlatformUtils
import com.jetbrains.python.sdk.add.PyAddSdkGroupPanel
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.isAssociatedWithModule
import com.jetbrains.python.sdk.poetry.POETRY_ICON
import com.jetbrains.python.sdk.poetry.detectPoetryEnvs
import com.jetbrains.python.sdk.poetry.sdkHomes
import kotlinx.coroutines.runBlocking
import java.util.function.Supplier

fun createPoetryPanel(
  project: Project?,
  module: Module?,
  existingSdks: List<Sdk>,
  newProjectPath: String?,
  context: UserDataHolder,
): PyAddSdkPanel {
  val newPoetryPanel = when {
    allowCreatingNewEnvironments(project) -> PyAddNewPoetryPanel(project, module, existingSdks, null, context)
    else -> null
  }
  val existingPoetryPanel = PyAddExistingPoetryEnvPanel(project, module, existingSdks, null, context)
  val panels = listOfNotNull(newPoetryPanel, existingPoetryPanel)
  val existingSdkPaths = sdkHomes(existingSdks)
  val defaultPanel = when {
    runBlockingCancellable {
      detectPoetryEnvs(module, existingSdkPaths, project?.basePath ?: newProjectPath)
    }.any { it.isAssociatedWithModule(module) } -> existingPoetryPanel
    newPoetryPanel != null -> newPoetryPanel
    else -> existingPoetryPanel
  }
  return PyAddSdkGroupPanel(Supplier { "Poetry environment" }, POETRY_ICON, panels, defaultPanel)
}

private fun allowCreatingNewEnvironments(project: Project?) =
  project != null || !PlatformUtils.isPyCharm() || PlatformUtils.isPyCharmEducational()