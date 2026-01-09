// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface KotlinCompilerPluginProjectConfigurator {

    val kotlinCompilerPluginId: String

    @RequiresWriteLock
    fun configureModule(module: Module): PsiFile?

    companion object {
        val EP_NAME: ExtensionPointName<KotlinCompilerPluginProjectConfigurator> =
            ExtensionPointName.create<KotlinCompilerPluginProjectConfigurator>("org.jetbrains.kotlin.compilerPluginConfigurator")
    }

}