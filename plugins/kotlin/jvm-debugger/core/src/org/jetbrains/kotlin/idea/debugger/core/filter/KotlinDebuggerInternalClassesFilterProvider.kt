// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.filter

import com.intellij.ui.classFilter.ClassFilter
import com.intellij.ui.classFilter.DebuggerClassFilterProvider
import org.jetbrains.kotlin.idea.debugger.KotlinDebuggerSettings

class KotlinDebuggerInternalClassesFilterProvider : DebuggerClassFilterProvider {
    override fun getFilters(): List<ClassFilter> {
        return if (KotlinDebuggerSettings.getInstance().disableKotlinInternalClasses) FILTERS else listOf()
    }
}

private val FILTERS = listOf(
    ClassFilter("kotlin.jvm*"),
    ClassFilter("kotlin.reflect*"),
    ClassFilter("kotlin.NoWhenBranchMatchedException"),
    ClassFilter("kotlin.TypeCastException"),
    ClassFilter("kotlin.KotlinNullPointerException")
)