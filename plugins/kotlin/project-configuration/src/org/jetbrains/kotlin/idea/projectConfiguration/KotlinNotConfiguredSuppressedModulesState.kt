// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectConfiguration

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup

@Service(Service.Level.PROJECT)
@State(name = "SuppressABINotification")
class KotlinNotConfiguredSuppressedModulesState :
    SerializablePersistentStateComponent<KotlinNotConfiguredSuppressedModulesState.State>(State()) {

    data class State(
        @JvmField @OptionTag val isSuppressed: Boolean = false,
        @JvmField @XCollection val modulesWithSuppressedNotConfigured: Set<String> = emptySet(),
    )

    companion object {
        fun getInstance(project: Project): KotlinNotConfiguredSuppressedModulesState = project.service()

        fun isSuppressed(project: Project): Boolean {
            return getInstance(project).state.isSuppressed
        }

        fun shouldSuggestConfiguration(module: Module): Boolean {
            return module.name !in getInstance(module.project).state.modulesWithSuppressedNotConfigured
        }

        internal fun suppressConfiguration(module: Module) {
            val moduleName = module.toModuleGroup().baseModule.name
            getInstance(module.project).updateState { state ->
                state.copy(modulesWithSuppressedNotConfigured = state.modulesWithSuppressedNotConfigured + moduleName)
            }
        }
    }
}