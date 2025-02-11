// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch.output

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.actions.RunScratchActionK2

interface ScratchOutputHandler {
    fun onStart(file: ScratchFile)
    fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput)
    fun handle(file: ScratchFile, infos: List<RunScratchActionK2.ExplainInfo>, scope: CoroutineScope) {}
    fun error(file: ScratchFile, message: String)
    fun onFinish(file: ScratchFile)
    fun clear(file: ScratchFile)
}

data class ScratchOutput(val text: String, val type: ScratchOutputType)

enum class ScratchOutputType {
    RESULT,
    OUTPUT,
    ERROR
}

open class ScratchOutputHandlerAdapter : ScratchOutputHandler {
    override fun onStart(file: ScratchFile) {}
    override fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput) {}
    override fun error(file: ScratchFile, message: String) {}
    override fun onFinish(file: ScratchFile) {}
    override fun clear(file: ScratchFile) {}
}