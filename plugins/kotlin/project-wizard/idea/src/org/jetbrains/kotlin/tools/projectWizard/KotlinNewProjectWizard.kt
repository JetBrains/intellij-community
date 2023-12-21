// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logBuildSystemChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logBuildSystemFinished
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.KOTLIN
import com.intellij.ide.wizard.*
import com.intellij.ide.wizard.LanguageNewProjectWizardData.Companion.languageData
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.ui.dsl.builder.*
import com.intellij.util.SystemProperties
import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.asPath
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.reference
import org.jetbrains.kotlin.tools.projectWizard.core.service.WizardKotlinVersion
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.StructurePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.gradle.GradlePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.KotlinPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.projectTemplates.applyProjectTemplate
import org.jetbrains.kotlin.tools.projectWizard.projectTemplates.ConsoleApplicationProjectTemplate
import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version
import org.jetbrains.kotlin.tools.projectWizard.wizard.KotlinNewProjectWizardUIBundle
import org.jetbrains.kotlin.tools.projectWizard.wizard.NewProjectWizardModuleBuilder
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import java.util.*

class KotlinNewProjectWizard : LanguageGeneratorNewProjectWizard {

    override val name = KOTLIN

    override val icon = KotlinIcons.SMALL_LOGO

    override val ordinal = 100

    companion object {
        private const val DEFAULT_GROUP_ID = "me.user"

        private fun Context.Reader.getWizardKotlinVersion(): WizardKotlinVersion {
            return KotlinPlugin.version.propertyValue
        }

        fun getKotlinWizardVersion(newProjectWizardModuleBuilder: NewProjectWizardModuleBuilder): WizardKotlinVersion {
            var wizardKotlinVersion: WizardKotlinVersion
            newProjectWizardModuleBuilder.apply {
                wizardKotlinVersion = wizard.context.Reader().getWizardKotlinVersion()
            }
            return wizardKotlinVersion
        }

        fun generateProject(
            project: Project,
            projectPath: String,
            projectName: String,
            isProject: Boolean = true, // false stands for module
            sdk: Sdk?,
            buildSystemType: BuildSystemType,
            projectGroupId: String? = suggestGroupId(),
            artifactId: String? = projectName,
            version: String? = "1.0-SNAPSHOT",
            addSampleCode: Boolean = true,
            gradleVersion: String? = null,
            gradleHome: String? = null,
            useCompactProjectStructure: Boolean = false,
            kotlinStdlib: LibraryOrderEntry? = null
        ) {
            NewProjectWizardModuleBuilder()
                .apply {
                    wizard.apply(emptyList(), setOf(GenerationPhase.PREPARE))
                    wizard.jdk = sdk
                    wizard.isCreatingNewProject = isProject
                    wizard.stdlibForJps = kotlinStdlib
                    wizard.context.writeSettings {
                        StructurePlugin.name.reference.setValue(projectName)
                        StructurePlugin.projectPath.reference.setValue(projectPath.asPath())
                        StructurePlugin.useCompactProjectStructure.reference.setValue(useCompactProjectStructure)
                        StructurePlugin.isCreatingNewProjectHierarchy.reference.setValue(isProject)

                        // If a local gradle installation was selected, we want to use the local gradle installation's
                        // version so that the wizard knows what kind of build scripts to generate
                        val actualGradleVersion = if (gradleHome != null) {
                            GradleInstallationManager.getGradleVersion(gradleHome) ?: gradleVersion
                        } else gradleVersion
                        actualGradleVersion?.let {
                            GradlePlugin.gradleVersion.reference.setValue(Version.fromString(it))
                        }
                        GradlePlugin.gradleHome.reference.setValue(gradleHome ?: "")

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

    // Uncommenting this line disables new Kotlin modules
    //override fun isEnabled(context: WizardContext): Boolean = context.isCreatingNewProject

    override fun createStep(parent: NewProjectWizardStep) = Step(parent)

    class Step(parent: NewProjectWizardStep) :
        AbstractNewProjectWizardMultiStep<Step, BuildSystemKotlinNewProjectWizard>(parent, BuildSystemKotlinNewProjectWizard.EP_NAME),
        LanguageNewProjectWizardData by parent.languageData!!,
        BuildSystemKotlinNewProjectWizardData {

        override val self = this
        override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")
        override val buildSystemProperty by ::stepProperty
        override var buildSystem by ::step

        override fun createAndSetupSwitcher(builder: Row): SegmentedButton<String> {
            return super.createAndSetupSwitcher(builder)
                .whenItemSelectedFromUi { logBuildSystemChanged() }
        }

        override fun setupProject(project: Project) {
            super.setupProject(project)

            logBuildSystemFinished()
        }

        init {
            data.putUserData(BuildSystemKotlinNewProjectWizardData.KEY, this)
        }
    }
}

fun NewProjectWizardStep.setupKmpWizardLinkUI(builder: Panel) {
    builder.row {
        text(KotlinNewProjectWizardUIBundle.message("project.wizard.new.project.kotlin.comment"),
             action = HyperlinkEventAction {
                 context.requestSwitchTo(NewProjectWizardModuleBuilder.MODULE_BUILDER_ID) { }
             })
            .applyToComponent { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }

        topGap(TopGap.SMALL)
        bottomGap(BottomGap.SMALL)
    }
}