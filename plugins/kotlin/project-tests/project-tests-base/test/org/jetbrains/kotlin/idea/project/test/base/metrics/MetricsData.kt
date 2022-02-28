// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base.metrics

import org.jetbrains.kotlin.idea.project.test.base.actions.ActionExecutionResultError

data class MetricsData(
    val iteration: Int,
    val metrics: Map<Metric, Long>,
    val errors: List<ActionExecutionResultError>,
) {
    val hasErrors: Boolean
        get() = errors.isNotEmpty()
}