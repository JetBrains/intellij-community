// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * The unique identifier of the terminal block.
 */
@ApiStatus.Experimental
@Serializable
sealed interface TerminalBlockId

@ApiStatus.Internal
@Serializable
data class TerminalBlockIdImpl(val id: Int) : TerminalBlockId