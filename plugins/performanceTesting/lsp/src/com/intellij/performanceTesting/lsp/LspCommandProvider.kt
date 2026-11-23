// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.lsp

import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand

internal class LspCommandProvider : CommandProvider {
  override fun getCommands(): Map<String, CreateCommand> = mapOf(
    LspWaitForEditorCommand.PREFIX to CreateCommand(::LspWaitForEditorCommand),
    LspWaitForDiagnosticCommand.PREFIX to CreateCommand(::LspWaitForDiagnosticCommand),
    LspWaitForSemanticTokenCommand.PREFIX to CreateCommand(::LspWaitForSemanticTokenCommand),
    LspWaitForDocumentHighlightCommand.PREFIX to CreateCommand(::LspWaitForDocumentHighlightCommand),
    LspWaitForInlayHintCommand.PREFIX to CreateCommand(::LspWaitForInlayHintCommand),
    LspWaitForFoldRegionCommand.PREFIX to CreateCommand(::LspWaitForFoldRegionCommand),
    LspWaitForCodeLensCommand.PREFIX to CreateCommand(::LspWaitForCodeLensCommand),
    LspFindUsagesCommand.PREFIX to CreateCommand(::LspFindUsagesCommand),
    LspHoverCommand.PREFIX to CreateCommand(::LspHoverCommand),
  )
}
