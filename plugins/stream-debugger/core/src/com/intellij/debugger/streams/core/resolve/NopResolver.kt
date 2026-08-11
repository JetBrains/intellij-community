// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.resolve

import com.intellij.debugger.streams.core.trace.TraceInfo

object NopResolver : ValuesOrderResolver {
    override fun resolve(info: TraceInfo): ValuesOrderResolver.Result {
        val direct = requireNotNull(info.directTrace) { "directTrace must be precomputed by the interpreter" }
        val reverse = requireNotNull(info.reverseTrace) { "reverseTrace must be precomputed by the interpreter" }

        return ValuesOrderResolver.Result.of(direct, reverse)
    }
}
