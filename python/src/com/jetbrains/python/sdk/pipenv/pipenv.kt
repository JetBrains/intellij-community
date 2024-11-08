// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.PathUtil
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.Icon

// TODO: Provide a special icon for pipenv
val PIPENV_ICON: Icon = PythonIcons.Python.PythonClosed

/**
 * Tells if the SDK was added as a pipenv.
 */
internal val Sdk.isPipEnv: Boolean
  get() = sdkAdditionalData is PyPipEnvSdkAdditionalData

@Internal
fun suggestedSdkName(basePath: @NlsSafe String): @NlsSafe String = "Pipenv (${PathUtil.getFileName(basePath)})"