// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.compilerRunner

import com.intellij.openapi.diagnostic.Logger

internal class JpsKotlinLogger(private val log: Logger) : KotlinLogger {
    override fun error(msg: String) {
        log.error(msg)
    }

    override fun warn(msg: String) {
        log.warn(msg)
    }

    override fun info(msg: String) {
        log.info(msg)
    }

    override fun debug(msg: String) {
        log.debug(msg)
    }

    override val isDebugEnabled: Boolean
        get() = log.isDebugEnabled
}