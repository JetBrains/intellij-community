// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * The system property that switches `search_*` results to one text line per hit.
 * `McpServerHeadlessStarter` sets it from `--search-format`.
 */
@Internal
const val SEARCH_COMPACT_OUTPUT_PROPERTY: String = "idea.mcp.server.search.compact.output"

/** True when this server renders search hits as text lines. See [SEARCH_COMPACT_OUTPUT_PROPERTY]. */
@Internal
fun isCompactSearchOutput(): Boolean = System.getProperty(SEARCH_COMPACT_OUTPUT_PROPERTY).toBoolean()
