// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface KotlinConfiguratorChangesViewer {
    fun showChanges(project: Project, changes: List<KotlinConfiguratorChangedFile>)

    companion object {
        private val EP_NAME: ExtensionPointName<KotlinConfiguratorChangesViewer> =
            ExtensionPointName.create("org.jetbrains.kotlin.configuratorChangesViewer")

        fun getInstance(): KotlinConfiguratorChangesViewer? = EP_NAME.extensionList.firstOrNull()
    }
}
