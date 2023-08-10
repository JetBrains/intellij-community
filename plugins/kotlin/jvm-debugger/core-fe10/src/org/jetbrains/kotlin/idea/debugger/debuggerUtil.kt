// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger

import com.sun.jdi.ReferenceType
import org.jetbrains.kotlin.idea.debugger.core.isInKotlinSources

@Deprecated(
    "use org.jetbrains.kotlin.idea.debugger.core.DebuggerUtil.isInKotlinSources instead",
    ReplaceWith("isInKotlinSources()", "org.jetbrains.kotlin.idea.debugger.core.isInKotlinSources")
)
fun ReferenceType.isInKotlinSources(): Boolean =
    isInKotlinSources()
