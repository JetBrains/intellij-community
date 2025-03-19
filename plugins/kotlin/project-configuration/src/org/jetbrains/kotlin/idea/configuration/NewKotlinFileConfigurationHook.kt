// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.kotlin.idea.base.projectStructure.NewKotlinFileHook
import org.jetbrains.kotlin.psi.KtFile

class NewKotlinFileConfigurationHook : NewKotlinFileHook() {
    override fun postProcess(createdElement: KtFile, module: Module) {
        val virtualFile = createdElement.virtualFile ?: return
        val fileIndex = ModuleRootManager.getInstance(module).fileIndex
        // The kotlin sourceDir is also included by JavaModuleSourceRootTypes.SOURCES
        if (!fileIndex.isUnderSourceRootOfType(virtualFile, JavaModuleSourceRootTypes.SOURCES)) {
            return
        }

        // New auto-config logic
        KotlinProjectConfigurationService.getInstance(module.project).runAutoConfigurationIfPossible(module)
    }
}