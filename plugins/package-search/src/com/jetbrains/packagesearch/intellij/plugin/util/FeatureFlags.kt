/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

private val Application.lifecycleScope: CoroutineScope
    get() {
        val scope = CoroutineScope(AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher())
        val connection = messageBus.simpleConnect()
        connection.subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
            override fun appClosing() {
                scope.cancel()
                connection.disconnect()
            }
        })
        return scope
    }

object FeatureFlags {

    val useDebugLogging: StateFlow<Boolean>
        get() = registryChangesFlow("packagesearch.plugin.debug.logging")
            .stateIn(ApplicationManager.getApplication().lifecycleScope, SharingStarted.Eagerly, Registry.`is`("packagesearch.plugin.debug.logging", false))

    val showRepositoriesTabFlow
        get() = registryChangesFlow("packagesearch.plugin.repositories.tab")

    val smartKotlinMultiplatformCheckboxEnabledFlow
        get() = registryChangesFlow("packagesearch.plugin.smart.kotlin.multiplatform.checkbox")
}

private fun registryChangesFlow(key: String, defaultValue: Boolean = false): Flow<Boolean> =
    ApplicationManager.getApplication().messageBusFlow(RegistryManager.TOPIC, { Registry.`is`(key, defaultValue) }) {
        object : RegistryValueListener {
            override fun afterValueChanged(value: RegistryValue) {
                if (value.key == key) trySend(Registry.`is`(key, defaultValue))
            }
        }
    }
