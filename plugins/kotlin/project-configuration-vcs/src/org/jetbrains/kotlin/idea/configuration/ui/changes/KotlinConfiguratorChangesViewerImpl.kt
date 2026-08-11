// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.ui.changes

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.configuration.KotlinConfiguratorChangedFile
import org.jetbrains.kotlin.idea.configuration.KotlinConfiguratorChangesViewer

internal class KotlinConfiguratorChangesViewerImpl : KotlinConfiguratorChangesViewer {
    override fun showChanges(project: Project, changes: List<KotlinConfiguratorChangedFile>) {
        KotlinConfiguratorChangesDialog(project, changes)
    }
}
