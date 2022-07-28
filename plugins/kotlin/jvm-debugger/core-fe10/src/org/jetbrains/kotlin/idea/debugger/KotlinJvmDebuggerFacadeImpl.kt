// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger

import org.jetbrains.kotlin.idea.extensions.KotlinJvmDebuggerFacade

class KotlinJvmDebuggerFacadeImpl : KotlinJvmDebuggerFacade {
    override val isCoroutineAgentAllowedInDebug: Boolean
        get() = !KotlinDebuggerSettings.getInstance().debugDisableCoroutineAgent
}