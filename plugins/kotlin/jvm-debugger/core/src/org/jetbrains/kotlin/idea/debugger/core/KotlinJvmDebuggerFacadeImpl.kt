// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import org.jetbrains.kotlin.idea.debugger.KotlinDebuggerSettings
import org.jetbrains.kotlin.idea.extensions.KotlinJvmDebuggerFacade

class KotlinJvmDebuggerFacadeImpl : KotlinJvmDebuggerFacade {
    override val isCoroutineAgentAllowedInDebug: Boolean
        get() = !KotlinDebuggerSettings.getInstance().debugDisableCoroutineAgent
}