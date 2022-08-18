// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.target

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.sdk.PyRemoteSdkValidator
import org.jetbrains.annotations.ApiStatus

/**
 * The extension allows to *visually* highlight target-based interpreters when the flag `python.use.targets.api` is disabled.
 *
 * **Note:** this extension is going to be removed as soon as possible after ultimate switching to Targets API.
 */
@ApiStatus.Internal
class PyTargetSdkValidator : PyRemoteSdkValidator {
  override fun isInvalid(sdk: Sdk): Boolean =
    !Registry.`is`("python.use.targets.api") && sdk.sdkAdditionalData is PyTargetAwareAdditionalData
}