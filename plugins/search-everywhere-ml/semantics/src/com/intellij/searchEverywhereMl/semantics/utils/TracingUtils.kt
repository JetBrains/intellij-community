package com.intellij.searchEverywhereMl.semantics.utils

import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val SEMANTIC_SEARCH_TRACER = TelemetryManager.getInstance().getTracer(Scope("semanticSearch"))