// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.project.Project
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.installer.BinaryInstallation

@Deprecated("This method was never intended to be a public API")
object PySdkToInstallManager {
  @Deprecated("This method was never intended to be a public API")
  fun getAvailableVersionsToInstall(): Map<LanguageLevel, BinaryInstallation> = getAvailableVersionsToInstall()

  @Deprecated("This method was never intended to be a public API")
  fun findInstalledSdk(
    languageLevel: LanguageLevel?,
    project: Project?,
    systemWideSdksDetector: () -> List<PyDetectedSdk>,
  ): PyDetectedSdk? = findInstalledSdkInternal(languageLevel, project, systemWideSdksDetector)
}