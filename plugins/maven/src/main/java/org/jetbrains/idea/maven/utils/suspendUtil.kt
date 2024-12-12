// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MavenSuspendUtil")
@file:ApiStatus.Internal

package org.jetbrains.idea.maven.utils

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.getEelApi
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

internal fun Path.getEelApiBlocking(): EelApi {
  @Suppress("RAW_RUN_BLOCKING")
  return runBlocking { getEelApi() }
}
