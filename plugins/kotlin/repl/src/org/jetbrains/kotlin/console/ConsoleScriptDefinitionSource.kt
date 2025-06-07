// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.console

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource

class ConsoleScriptDefinitionSource : ScriptDefinitionsSource {

    private val definitionsSet = ConcurrentCollectionFactory.createConcurrentSet<ScriptDefinition>()

    override val definitions: Sequence<ScriptDefinition>
        get() = definitionsSet.asSequence()

    fun registerDefinition(definition: ScriptDefinition) {
        definitionsSet.add(definition)
    }

    fun unregisterDefinition(definition: ScriptDefinition) {
        definitionsSet.remove(definition)
    }

    companion object {
        fun getInstance(project: Project): ConsoleScriptDefinitionSource? =
            SCRIPT_DEFINITIONS_SOURCES.getExtensions(project)
                .filterIsInstance<ConsoleScriptDefinitionSource>()
                .singleOrNull()
    }
}