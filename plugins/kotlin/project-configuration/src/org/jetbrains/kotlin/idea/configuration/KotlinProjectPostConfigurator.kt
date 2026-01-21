// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface KotlinProjectPostConfigurator {

    val name: String

    fun isApplicable(module: Module): Boolean

    @RequiresWriteLock
    fun configureModule(module: Module)

    companion object {
        val EP_NAME: ExtensionPointName<KotlinProjectPostConfigurator> =
            ExtensionPointName.create("org.jetbrains.kotlin.projectPostConfigurator")
    }
}
