// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.components.service
import com.intellij.platform.eel.EelApi
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration

internal class FileSystemFactoryImpl : FileSystemFactory {
  override fun create(eelApi: EelApi): FileSystemWithEel = EelFileSystem(eelApi)

  override fun create(target: TargetEnvironmentConfiguration): FileSystem<PathHolder.Target> =
    service<TargetFileSystemCache>().getOrCreate(target, PythonLanguageRuntimeConfiguration())
}
