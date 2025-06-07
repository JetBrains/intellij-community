// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.performancePlugin.utils

import java.lang.management.ManagementFactory

object JvmRuntimeUtils {
    fun getGCTime(): Long {
        var result: Long = 0
        for (garbageCollectorMXBean in ManagementFactory.getGarbageCollectorMXBeans()) {
            result += garbageCollectorMXBean.collectionTime
        }
        return result
    }

    fun getJitTime(): Long {
        return ManagementFactory.getCompilationMXBean().totalCompilationTime
    }
}