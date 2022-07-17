// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.add.PyAddSdkView

interface PyAddTargetBasedSdkView : PyAddSdkView {
  fun getOrCreateSdk(targetEnvironmentConfiguration: TargetEnvironmentConfiguration?): Sdk? = getOrCreateSdk()
}