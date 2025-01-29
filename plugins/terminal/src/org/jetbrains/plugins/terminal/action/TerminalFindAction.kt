// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import org.jetbrains.plugins.terminal.block.TerminalPromotedEditorAction
import org.jetbrains.plugins.terminal.block.output.TerminalFindHandler

internal class TerminalFindAction : TerminalPromotedEditorAction(TerminalFindHandler())
