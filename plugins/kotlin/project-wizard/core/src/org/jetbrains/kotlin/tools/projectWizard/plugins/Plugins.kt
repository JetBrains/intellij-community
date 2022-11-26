// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.plugins

import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.Plugin
import org.jetbrains.kotlin.tools.projectWizard.core.buildPersistenceList
import org.jetbrains.kotlin.tools.projectWizard.core.service.BuildSystemAvailabilityWizardService
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.JpsPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.MavenPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.gradle.GroovyDslPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.gradle.KotlinDslPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.KotlinPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.projectTemplates.ProjectTemplatesPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.templates.*

object Plugins {
    val allPlugins: (Context) -> List<Plugin> = { context: Context ->
        val buildSystemService = context.read {
            service<BuildSystemAvailabilityWizardService>()
        }

        buildPersistenceList<Plugin> {
            +StructurePlugin(context)
            if (buildSystemService.isAvailable(BuildSystemType.GradleGroovyDsl))
                +GroovyDslPlugin(context)

            if (buildSystemService.isAvailable(BuildSystemType.GradleKotlinDsl))
                +KotlinDslPlugin(context)

            +JpsPlugin(context)

            if (buildSystemService.isAvailable(BuildSystemType.Maven))
                +MavenPlugin(context)

            +KotlinPlugin(context)
            +TemplatesPlugin(context)
            +ProjectTemplatesPlugin(context)
            +RunConfigurationsPlugin(context)
            +InspectionsPlugin(context)
            +AndroidPlugin(context)

            // templates
            +ConsoleJvmApplicationTemplatePlugin(context)
            +KtorTemplatesPlugin(context)
            +JsTemplatesPlugin(context)
            +ReactJsTemplatesPlugin(context)
            +SimpleNodeJsTemplatesPlugin(context)
            +NativeConsoleApplicationTemplatePlugin(context)
            +MobileMppTemplatePlugin(context)
        }
    }
}