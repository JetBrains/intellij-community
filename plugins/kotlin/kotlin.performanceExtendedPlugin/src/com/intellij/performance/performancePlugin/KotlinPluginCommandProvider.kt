// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performance.performancePlugin

import com.intellij.performance.performancePlugin.commands.AssertKotlinFileInSpecificRootCommand
import com.intellij.performance.performancePlugin.commands.ClearLibraryCaches
import com.intellij.performance.performancePlugin.commands.ClearSourceCaches
import com.intellij.performance.performancePlugin.commands.GCCommand
import com.intellij.performance.performancePlugin.commands.KotlinEditorOptionsChangeCommand
import com.intellij.performance.performancePlugin.commands.CreateKotlinFileCommand
import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand

class KotlinPluginCommandProvider : CommandProvider {

    override fun getCommands(): MutableMap<String, CreateCommand> {
        return mutableMapOf(
            ClearSourceCaches.PREFIX to CreateCommand(::ClearSourceCaches),
            ClearLibraryCaches.PREFIX to CreateCommand(::ClearLibraryCaches),
            GCCommand.PREFIX to CreateCommand(::GCCommand),
            AssertKotlinFileInSpecificRootCommand.PREFIX to CreateCommand(::AssertKotlinFileInSpecificRootCommand),
            KotlinEditorOptionsChangeCommand.PREFIX to CreateCommand(::KotlinEditorOptionsChangeCommand),
            CreateKotlinFileCommand.PREFIX to CreateCommand(::CreateKotlinFileCommand)
        )
    }
}