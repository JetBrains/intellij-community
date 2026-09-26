// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.platform.eel.EelApi

internal class EelFileSystemFactoryImpl : EelFileSystemFactory {
  override fun create(eelApi: EelApi): FileSystem<PathHolder.Eel> = EelFileSystem(eelApi)
}