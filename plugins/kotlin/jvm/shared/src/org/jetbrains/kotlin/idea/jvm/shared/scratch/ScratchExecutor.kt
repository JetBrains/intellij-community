// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyrig()ht 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.jvm.shared.scratch

import com.intellij.openapi.diagnostic.ControlFlowException
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.ScratchCompilationSupport
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ExplainInfo
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutput
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutputHandler
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutputHandlerAdapter

abstract class ScratchExecutor(protected open val file: ScratchFile) {
    protected val handler: CompositeOutputHandler

    init {
        handler = CompositeOutputHandler()

        addOutputHandler(object : ScratchOutputHandlerAdapter() {
            override fun onStart(file: ScratchFile) {
                ScratchCompilationSupport.start(file, this@ScratchExecutor)
            }

            override fun onFinish(file: ScratchFile) {
                ScratchCompilationSupport.stop()
            }
        })
    }

    abstract fun execute()
    abstract fun stop()


    fun addOutputHandler(outputHandler: ScratchOutputHandler) {
        handler.add(outputHandler)
    }

    fun errorOccurs(message: String, e: Throwable? = null, isFatal: Boolean = false) {
        handler.error(file, message)

        if (isFatal) {
            handler.onFinish(file)
        }

        if (e != null && (e !is ControlFlowException)) LOG.error(e)
    }

    class CompositeOutputHandler : ScratchOutputHandler {
        private val handlers = mutableSetOf<ScratchOutputHandler>()

        fun add(handler: ScratchOutputHandler) {
            handlers.add(handler)
        }

        fun remove(handler: ScratchOutputHandler) {
            handlers.remove(handler)
        }

        override fun onStart(file: ScratchFile) {
            handlers.forEach { it.onStart(file) }
        }

        override fun handle(file: ScratchFile, explanations: List<ExplainInfo>, scope: CoroutineScope) {
            handlers.forEach { it.handle(file, explanations, scope) }
        }

        override fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput) {
            handlers.forEach { it.handle(file, expression, output) }
        }

        override fun error(file: ScratchFile, message: String) {
            handlers.forEach { it.error(file, message) }
        }

        override fun onFinish(file: ScratchFile) {
            handlers.forEach { it.onFinish(file) }
        }

        override fun clear(file: ScratchFile) {
            handlers.forEach { it.clear(file) }
        }
    }
}

