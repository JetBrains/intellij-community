// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performance.performancePlugin

import com.intellij.performance.performancePlugin.commands.*
import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand

internal class KotlinPluginCommandProvider : CommandProvider {

    override fun getCommands(): Map<String, CreateCommand> = mapOf(
        ClearSourceCaches.PREFIX to CreateCommand(::ClearSourceCaches),
        ClearLibraryCaches.PREFIX to CreateCommand(::ClearLibraryCaches),
        AssertKotlinFileInSpecificRootCommand.PREFIX to CreateCommand(::AssertKotlinFileInSpecificRootCommand),
        KotlinEditorOptionsChangeCommand.PREFIX to CreateCommand(::KotlinEditorOptionsChangeCommand),
        CreateKotlinFileCommand.PREFIX to CreateCommand(::CreateKotlinFileCommand),
        TypingWithCompletionCommand.PREFIX to CreateCommand(::TypingWithCompletionCommand),
        EnableKotlinDaemonLogCommand.PREFIX to CreateCommand(::EnableKotlinDaemonLogCommand),
        AddKotlinCompilerOptionsCommand.PREFIX to CreateCommand(::AddKotlinCompilerOptionsCommand),
        ConvertJavaToKotlinCommand.PREFIX to CreateCommand(::ConvertJavaToKotlinCommand)
    )
}