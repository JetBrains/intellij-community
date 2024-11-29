// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.incremental.utils

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import java.util.*

/**
 * This class needs to be mocked for current bundled JPS versions (those prior to Kotlin 2.1.20), as `TestMessageCollector` is required by
 * the bundled JPS plugin but was removed with the following commit and is thus absent from the bundled Kotlin compiler:
 * [6fc9075](https://github.com/JetBrains/kotlin/commit/6fc9075db5dae812da14ffd0175a00eb3512bb6b).
 */
@Suppress("unused")
class TestMessageCollector : MessageCollector {
    val errors = ArrayList<String>()

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
        if (severity.isError) {
            errors.add(message)
        }
    }

    override fun clear() {
        errors.clear()
    }

    override fun hasErrors(): Boolean =
        errors.isNotEmpty()
}
