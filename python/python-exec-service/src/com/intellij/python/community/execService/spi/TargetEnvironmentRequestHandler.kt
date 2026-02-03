// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.spi

import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

interface TargetEnvironmentRequestHandler {

  fun isApplicable(request: TargetEnvironmentRequest): Boolean

  fun mapUploadRoots(request: TargetEnvironmentRequest, localDirs: Set<Path>): List<TargetEnvironment.UploadRoot>

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<TargetEnvironmentRequestHandler> = ExtensionPointName.create<TargetEnvironmentRequestHandler>(
      "Pythonid.execService.targetEnvironmentRequestHandler"
    )

    @JvmStatic
    @ApiStatus.Internal
    fun getHandler(request: TargetEnvironmentRequest): TargetEnvironmentRequestHandler {
      val handler = EP_NAME.extensionList.firstOrNull { it.isApplicable(request) }
                    ?: error("No implementation of [${TargetEnvironmentRequestHandler::class.java}] is found for $request, bundle is broken?")
      return handler
    }
  }
}
