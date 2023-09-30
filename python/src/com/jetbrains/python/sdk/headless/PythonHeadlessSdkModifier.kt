// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.headless

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to modify the used python SDK for a project.
 * The motivation here is that SDK is handled differently depending on the distribution of the IDE:
 * In IDEA, Python SDK is added via the facet machinery, whereas in PyCharm it is simply a module SDK.
 */
@ApiStatus.Internal
interface PythonHeadlessSdkModifier {

  companion object {
    val EP_NAME: ExtensionPointName<PythonHeadlessSdkModifier> = ExtensionPointName("Pythonid.pythonHeadlessSdkModifier")
  }

  /**
   * @return if this extension managed to modify SDK. If `false`, default PyCharm logic will be used, which sets the SDK on module level.
   */
  fun setSdk(project: Project, sdk: Sdk) : Boolean
}