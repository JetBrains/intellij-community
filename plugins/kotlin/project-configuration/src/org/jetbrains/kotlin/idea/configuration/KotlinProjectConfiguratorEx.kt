// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface KotlinProjectConfiguratorEx: KotlinProjectConfigurator {
    fun configureModule(module: Module): PsiFile?
}