package com.intellij.terminal.frontend.view.typeahead

import com.intellij.terminal.frontend.view.impl.TerminalOutputModelController
import org.jetbrains.annotations.ApiStatus

/**
 * A single interface for different [TerminalOutputModelController] implementations with Type-Ahead support.
 */
@ApiStatus.Internal
interface TerminalTypeAheadOutputModelController : TerminalOutputModelController, TerminalTypeAhead