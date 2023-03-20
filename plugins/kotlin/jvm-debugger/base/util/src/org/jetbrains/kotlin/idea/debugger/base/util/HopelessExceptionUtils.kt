// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("HopelessExceptionUtils")

package org.jetbrains.kotlin.idea.debugger.base.util

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.openapi.diagnostic.Logger
import com.sun.jdi.IncompatibleThreadStateException
import com.sun.jdi.VMDisconnectedException
import org.jetbrains.annotations.ApiStatus

private val LOG = Logger.getInstance("HopelessExceptionUtils")

@ApiStatus.Internal
inline fun <T : Any> hopelessAware(block: () -> T?): T? {
    return try {
        block()
    } catch (e: Exception) {
        handleHopelessException(e)
        null
    }
}

@ApiStatus.Internal
fun handleHopelessException(e: Exception) {
    when (if (e is EvaluateException) e.cause ?: e else e) {
        is IncompatibleThreadStateException, is VMDisconnectedException -> {}
        else -> {
            if (e is EvaluateException) {
                LOG.debug("Cannot evaluate async stack trace", e)
            } else {
                throw e
            }
        }
    }
}