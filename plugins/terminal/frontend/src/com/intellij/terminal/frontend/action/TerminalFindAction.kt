// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import org.jetbrains.plugins.terminal.block.TerminalFrontendEditorAction

internal class TerminalFindAction : TerminalFrontendEditorAction(TerminalFindHandler(originalHandler = null))
