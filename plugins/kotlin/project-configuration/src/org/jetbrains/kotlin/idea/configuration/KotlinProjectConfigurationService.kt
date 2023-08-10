// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle

@Service(Service.Level.PROJECT)
class KotlinProjectConfigurationService(private val coroutineScope: CoroutineScope) {

    companion object {
        fun getInstance(project: Project): KotlinProjectConfigurationService {
            return project.service()
        }
    }


    fun runAutoConfigurationIfPossible(module: Module) {
        coroutineScope.launch(Dispatchers.Default) {
            val autoConfigurator = readAction {
                KotlinProjectConfigurator.EP_NAME.extensions
                    .firstOrNull { it.canRunAutoConfig() && it.isApplicable(module) }
            } ?: return@launch

            val autoConfigSettings = withBackgroundProgress(
                project = module.project,
                title = KotlinProjectConfigurationBundle.message("auto.configure.kotlin.check")
            ) {
                autoConfigurator.calculateAutoConfigSettings(module)
            }

            if (autoConfigSettings == null) return@launch
            withContext(Dispatchers.EDT) {
                autoConfigurator.runAutoConfig(autoConfigSettings)
            }
        }
    }
}