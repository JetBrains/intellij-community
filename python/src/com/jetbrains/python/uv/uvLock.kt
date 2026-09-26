// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.uv

import com.intellij.openapi.module.Module
import com.intellij.python.uv.common.UV_TOOL_ID
import com.intellij.util.FileName
import com.jetbrains.python.impl.getSdkAssociatedModule
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.project.resolveFile
import java.nio.file.Path

val UV_LOCK: FileName = FileName("uv.lock")

/**
 * The `uv.lock` that governs [module], or `null` when there is none.
 *
 * A uv workspace keeps one lock file, and it keeps it at the workspace root. So a member is answered from that root
 * and not from its own directory, which holds no lock. Read from the member alone, the lock of a synchronized
 * workspace looked absent, and the uv configurator then declined every member of it (PY-92193).
 */
suspend fun findUvLock(module: Module): Path? = module.getSdkAssociatedModule(UV_TOOL_ID).asPyProject()?.resolveFile(UV_LOCK)
