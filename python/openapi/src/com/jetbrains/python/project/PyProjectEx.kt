// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.project

import com.intellij.openapi.project.Project
import com.intellij.util.FileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.exists

@ApiStatus.Internal
suspend fun PyProject.resolveFile(fileName: FileName): Path? = resolveFile(fileName.value)

@ApiStatus.Internal
suspend fun PyProject.resolveFile(fileName: String): Path? = withContext(Dispatchers.IO) {
  baseDir.resolve(fileName).takeIf { it.exists() }
}


@get:ApiStatus.Internal
val PyProject.project: Project get() = residesOnModule.project
