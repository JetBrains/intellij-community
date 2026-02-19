// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JComponent
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

interface CreateContentParamsProvider {
    fun createContentParams(): CreateContentParams
}

data class CreateContentParams(
    val id: String,
    val component: JComponent,
    @Nls val displayName: String,
    val icon: Icon?,
    val parentComponent: JComponent
)


interface XDebugSessionListenerProvider {
    fun debugSessionListener(session: XDebugSession): XDebugSessionListener
}

/**
 * Logger instantiation: 'val log by logger'
 */
val logger: ReadOnlyProperty<Any, Logger> get() = LoggerDelegate()

class LoggerDelegate : ReadOnlyProperty<Any, Logger> {
    lateinit var logger: Logger

    override fun getValue(thisRef: Any, property: KProperty<*>): Logger {
        if (!::logger.isInitialized) {
            logger = Logger.getInstance(thisRef.javaClass)
        }
        return logger
    }
}
