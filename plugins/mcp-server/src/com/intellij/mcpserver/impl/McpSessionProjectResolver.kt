// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.openapi.project.Project

interface McpSessionProjectResolver {
  suspend fun resolveSessionProject(
    projectPathFromArgument: String?,
    projectPathFromCallHeader: String?,
    projectPathFromSessionHeader: String?,
    roots: Set<String>,
  ): Project
}