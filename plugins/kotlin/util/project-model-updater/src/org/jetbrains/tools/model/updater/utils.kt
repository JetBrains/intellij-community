// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import kotlin.system.exitProcess

internal fun exitWithErrorMessage(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
