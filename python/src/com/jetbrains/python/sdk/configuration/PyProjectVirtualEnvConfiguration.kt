// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyProjectVirtualEnvConfiguration")

package com.jetbrains.python.sdk.configuration

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.PySdkSettings

fun findPreferredVirtualEnvBaseSdk(existingBaseSdks: List<Sdk>): Sdk? {
  val preferredSdkPath = PySdkSettings.instance.preferredVirtualEnvBaseSdk.takeIf(FileUtil::exists)
  val detectedPreferredSdk = existingBaseSdks.find { it.homePath == preferredSdkPath }
  return when {
    detectedPreferredSdk != null -> detectedPreferredSdk
    preferredSdkPath != null -> PyDetectedSdk(preferredSdkPath)
    else -> existingBaseSdks.getOrNull(0)
  }
}

