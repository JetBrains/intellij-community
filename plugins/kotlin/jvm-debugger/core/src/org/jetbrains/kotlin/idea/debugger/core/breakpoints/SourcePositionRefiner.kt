// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.debugger.SourcePosition

interface SourcePositionRefiner {
    fun refineSourcePosition(sourcePosition: SourcePosition): SourcePosition
}
