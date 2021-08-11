// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.messages.CompilerMessage

fun jpsReportInternalBuilderError(context: CompileContext, error: Throwable) {
    val builderError = CompilerMessage.createInternalBuilderError("Kotlin", error)
    context.processMessage(builderError)
}