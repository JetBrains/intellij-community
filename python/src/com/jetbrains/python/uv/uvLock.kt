// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.uv

import com.intellij.openapi.module.Module
import com.intellij.util.FileName
import com.jetbrains.python.sdk.findAmongRoots
import java.nio.file.Path

val UV_LOCK: FileName = FileName("uv.lock")

suspend fun findUvLock(module: Module): Path? = module.findAmongRoots(UV_LOCK)
