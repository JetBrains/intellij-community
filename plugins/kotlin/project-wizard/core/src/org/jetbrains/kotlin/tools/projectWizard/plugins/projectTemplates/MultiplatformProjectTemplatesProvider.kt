// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.plugins.projectTemplates

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.tools.projectWizard.projectTemplates.ProjectTemplate

interface MultiplatformProjectTemplatesProvider {
    companion object {
        val EP_NAME = ExtensionPointName<MultiplatformProjectTemplatesProvider>("org.jetbrains.kotlin.tools.projectWizard.plugins.projectTemplates.multiplatformProjectTemplatesProvider")
    }

    fun addTemplate(result: MutableList<ProjectTemplate>)
}