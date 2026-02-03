// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectConfiguration

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup

@Service(Service.Level.PROJECT)
@State(name = "SuppressABINotification")
class KotlinNotConfiguredSuppressedModulesState : PersistentStateComponent<KotlinNotConfiguredSuppressedModulesState> {
    private var isSuppressed = false
    private var modulesWithSuppressedNotConfigured = sortedSetOf<String>()

    override fun getState(): KotlinNotConfiguredSuppressedModulesState = this

    override fun loadState(state: KotlinNotConfiguredSuppressedModulesState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): KotlinNotConfiguredSuppressedModulesState = project.service()

        fun isSuppressed(project: Project): Boolean {
            return getInstance(project).isSuppressed
        }

        fun shouldSuggestConfiguration(module: Module): Boolean {
            return module.name !in getInstance(module.project).modulesWithSuppressedNotConfigured
        }

        internal fun suppressConfiguration(module: Module) {
            getInstance(module.project).modulesWithSuppressedNotConfigured.add(module.toModuleGroup().baseModule.name)
        }
    }
}