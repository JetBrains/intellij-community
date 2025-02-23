// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.scratch.output

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile

interface ScratchOutputHandler {
    fun onStart(file: ScratchFile)
    fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput)
    fun handle(file: ScratchFile, infos: List<ExplainInfo>, scope: CoroutineScope) {}
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

class ExplainInfo(
    val variableName: String, val offsets: Pair<Int, Int>, val variableValue: Any?, val line: Int?
) {
    override fun toString(): String {
        return "ExplainInfo(variableName='$variableName', offsets=$offsets, variableValue=$variableValue, line=$line)"
    }
}

open class ScratchOutputHandlerAdapter : ScratchOutputHandler {
    override fun onStart(file: ScratchFile) {}
    override fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput) {}
    override fun error(file: ScratchFile, message: String) {}
    override fun onFinish(file: ScratchFile) {}
    override fun clear(file: ScratchFile) {}
}