// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logBuildSystemChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logBuildSystemFinished
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.util.SystemProperties
import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.tools.projectWizard.core.asPath
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.reference
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.StructurePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.projectTemplates.applyProjectTemplate
import org.jetbrains.kotlin.tools.projectWizard.projectTemplates.*
import org.jetbrains.kotlin.tools.projectWizard.wizard.NewProjectWizardModuleBuilder
import java.util.*

class KotlinNewProjectWizard : LanguageNewProjectWizard {
    override val ordinal = 100

    companion object {
        private const val DEFAULT_GROUP_ID = "me.user"

        fun generateProject(
            project: Project,
            projectPath: String,
            projectName: String,
            sdk: Sdk?,
            buildSystemType: BuildSystemType,
            projectGroupId: String? = suggestGroupId(),
            artifactId: String? = projectName,
            version: String? = "1.0-SNAPSHOT",
            addSampleCode: Boolean = true
        ) {
            NewProjectWizardModuleBuilder()
                .apply {
                    wizard.apply(emptyList(), setOf(GenerationPhase.PREPARE))
                    wizard.jdk = sdk
                    wizard.context.writeSettings {
                        StructurePlugin.name.reference.setValue(projectName)
                        StructurePlugin.projectPath.reference.setValue(projectPath.asPath())

                        projectGroupId?.let { StructurePlugin.groupId.reference.setValue(it) }
                        artifactId?.let { StructurePlugin.artifactId.reference.setValue(it) }
                        version?.let { StructurePlugin.version.reference.setValue(it) }

                        BuildSystemPlugin.type.reference.setValue(buildSystemType)

                        applyProjectTemplate(ConsoleApplicationProjectTemplate(addSampleCode = addSampleCode))
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

    override fun isEnabled(context: WizardContext): Boolean = context.isCreatingNewProject

    override fun createStep(parent: NewProjectWizardLanguageStep) = Step(parent)

    class Step(parent: NewProjectWizardLanguageStep) :
        AbstractNewProjectWizardMultiStep<Step, BuildSystemKotlinNewProjectWizard>(parent, BuildSystemKotlinNewProjectWizard.EP_NAME),
        LanguageNewProjectWizardData by parent,
        BuildSystemKotlinNewProjectWizardData {

        override val self = this
        override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")
        override val buildSystemProperty by ::stepProperty
        override var buildSystem by ::step

        override fun setupProject(project: Project) {
            super.setupProject(project)

            logBuildSystemFinished()
        }

        init {
            data.putUserData(BuildSystemKotlinNewProjectWizardData.KEY, this)

            buildSystemProperty.afterChange { logBuildSystemChanged() }
        }
    }
}

fun Panel.kmpWizardLink(context: WizardContext) {
    this.row {
        text(KotlinBundle.message("project.wizard.new.project.kotlin.comment"),
             action = HyperlinkEventAction {
                 context.requestSwitchTo(NewProjectWizardModuleBuilder.MODULE_BUILDER_ID) { }
             })
            .applyToComponent { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }

        topGap(TopGap.SMALL)
        bottomGap(BottomGap.SMALL)
    }
}