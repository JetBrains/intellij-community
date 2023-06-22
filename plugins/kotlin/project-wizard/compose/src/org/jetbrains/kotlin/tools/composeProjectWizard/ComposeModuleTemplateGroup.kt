// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.composeProjectWizard

import com.intellij.icons.AllIcons
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory


internal class ComposeModuleTemplateGroup : FileTemplateGroupDescriptorFactory {
    override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
        val root = FileTemplateGroupDescriptor("COMPOSE", AllIcons.Nodes.Module)

        with (root) {
            addTemplate(COMPOSE_SETTINGS_GRADLE)
            addTemplate(COMPOSE_GRADLE_PROPERTIES)
            addTemplate(COMPOSE_GRADLE_WRAPPER_PROPERTIES)
            addTemplate(COMPOSE_LOCAL_PROPERTIES)
            addTemplate(COMPOSE_DESKTOP_BUILD_GRADLE)
            addTemplate(COMPOSE_DESKTOP_MAINKT)
            addTemplate(COMPOSE_DESKTOP_RUN_CONFIGURATION_XML)
        }

        return root
    }

    companion object {
        const val COMPOSE_SETTINGS_GRADLE = "compose-settings.gradle.kts"
        const val COMPOSE_GRADLE_PROPERTIES = "compose-gradle.properties"
        const val COMPOSE_GRADLE_WRAPPER_PROPERTIES = "compose-gradle-wrapper.properties"
        const val COMPOSE_LOCAL_PROPERTIES = "compose-local.properties"
        const val COMPOSE_DESKTOP_BUILD_GRADLE = "compose-desktop-build.gradle.kts"
        const val COMPOSE_DESKTOP_MAINKT = "compose-desktop-main.kt"
        const val COMPOSE_DESKTOP_RUN_CONFIGURATION_XML = "compose-desktop-run-configuration.xml"
    }
}