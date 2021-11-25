// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.wizard.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.EMPTY_LABEL
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.SystemProperties
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.tools.projectWizard.core.asPath
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.reference
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.StructurePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.projectTemplates.applyProjectTemplate
import org.jetbrains.kotlin.tools.projectWizard.projectTemplates.ConsoleApplicationProjectTemplate
import org.jetbrains.kotlin.tools.projectWizard.wizard.NewProjectWizardModuleBuilder
import java.util.*

class KotlinNewProjectWizard : LanguageNewProjectWizard {

    companion object {
        private const val DEFAULT_GROUP_ID = "me.user"

        fun generateProject(
            presetBuilder: NewProjectWizardModuleBuilder? = null,
            project: Project,
            projectPath: String,
            projectName: String,
            sdk: Sdk?,
            buildSystemType: BuildSystemType,
            projectGroupId: String? = suggestGroupId(),
            artifactId: String? = projectName,
            version: String? = "1.0-SNAPSHOT"
        ) {
            val builder = presetBuilder ?: NewProjectWizardModuleBuilder()
            builder.apply {
                wizard.apply(emptyList(), setOf(GenerationPhase.PREPARE))

                wizard.jdk = sdk
                wizard.context.writeSettings {
                    StructurePlugin.name.reference.setValue(projectName)
                    StructurePlugin.projectPath.reference.setValue(projectPath.asPath())

                    projectGroupId?.let { StructurePlugin.groupId.reference.setValue(it) }
                    artifactId?.let { StructurePlugin.artifactId.reference.setValue(it) }
                    version?.let { StructurePlugin.version.reference.setValue(it) }

                    BuildSystemPlugin.type.reference.setValue(buildSystemType)

                    applyProjectTemplate(ConsoleApplicationProjectTemplate)
                }
            }.commit(project, null, null)
        }

        private fun suggestGroupId(): String {
            val username = SystemProperties.getUserName()
            if (!username.matches("[\\w\\s]+".toRegex())) return DEFAULT_GROUP_ID
            val usernameAsGroupId = username.trim().lowercase(Locale.getDefault()).split("\\s+".toRegex()).joinToString(separator = ".")
            return "me.$usernameAsGroupId"
        }
    }

    override val name: String = "Kotlin"

    override fun createStep(parent: NewProjectWizardLanguageStep) =
        CommentStep(parent)
            .chain(::Step)

    class CommentStep(parent: NewProjectWizardLanguageStep) :
        AbstractNewProjectWizardStep(parent),
        LanguageNewProjectWizardData by parent {

        override fun setupUI(builder: Panel) {
            with(builder) {
                row(EMPTY_LABEL) {
                    comment(KotlinBundle.message("project.wizard.new.project.kotlin.comment")) {
                        context.requestSwitchTo(NewProjectWizardModuleBuilder.MODULE_BUILDER_ID)
                    }
                }.bottomGap(BottomGap.SMALL)
            }
        }

        override fun setupProject(project: Project) {}
    }

    class Step(parent: CommentStep) :
        AbstractNewProjectWizardMultiStep<Step>(parent, BuildSystemKotlinNewProjectWizard.EP_NAME),
        LanguageNewProjectWizardData by parent,
        BuildSystemKotlinNewProjectWizardData {

        override val self = this
        override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")
        override val buildSystemProperty by ::stepProperty
        override var buildSystem by ::step

        init {
            data.putUserData(BuildSystemKotlinNewProjectWizardData.KEY, this)
        }
    }
}