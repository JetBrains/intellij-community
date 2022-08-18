// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base.metrics

object DefaultMetrics {
    val valueMetric = Metric("value")
    val gcTime = Metric("gc")
    val jitTime = Metric("jit")
}