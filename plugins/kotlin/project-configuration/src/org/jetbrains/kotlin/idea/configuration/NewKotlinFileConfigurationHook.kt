// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.projectStructure.NewKotlinFileHook
import org.jetbrains.kotlin.psi.KtFile

class NewKotlinFileConfigurationHook : NewKotlinFileHook() {
    override fun postProcess(createdElement: KtFile, module: Module) {
        // New auto-config logic
        KotlinProjectConfigurationService.getInstance(module.project).runAutoConfigurationIfPossible(module) { configured ->
            if (!configured) {
                showConfigureKotlinNotificationIfNeeded(module)
            }
        }
    }
}