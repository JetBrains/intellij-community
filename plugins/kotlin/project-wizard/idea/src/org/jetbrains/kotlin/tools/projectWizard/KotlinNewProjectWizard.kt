// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.generators.NewProjectWizardSdkData
import com.intellij.ide.wizard.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.columns
import com.intellij.util.SystemProperties
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.kotlin.tools.projectWizard.core.div
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.reference
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.StructurePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.projectTemplates.applyProjectTemplate
import org.jetbrains.kotlin.tools.projectWizard.projectTemplates.ConsoleApplicationProjectTemplate
import org.jetbrains.kotlin.tools.projectWizard.wizard.NewProjectWizardModuleBuilder
import java.nio.file.Path
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
            val modules = builder.apply {
                wizard.apply(emptyList(), setOf(GenerationPhase.PREPARE))

                wizard.jdk = sdk
                wizard.context.writeSettings {
                    StructurePlugin.name.reference.setValue(projectName)
                    StructurePlugin.projectPath.reference.setValue(projectPath / projectName)

                    projectGroupId?.let { StructurePlugin.groupId.reference.setValue(it) }
                    artifactId?.let { StructurePlugin.artifactId.reference.setValue(it) }
                    version?.let { StructurePlugin.version.reference.setValue(it) }

                    BuildSystemPlugin.type.reference.setValue(buildSystemType)

                    applyProjectTemplate(ConsoleApplicationProjectTemplate)
                }
            }.commit(project, null, null)

            val module = modules?.get(0) ?: return
            openSourceFileInEditor(module, ConsoleApplicationProjectTemplate.fileToOpenInEditor)
        }

        private fun suggestGroupId(): String {
            val username = SystemProperties.getUserName()
            if (!username.matches("[\\w\\s]+".toRegex())) return DEFAULT_GROUP_ID
            val usernameAsGroupId = username.trim().lowercase(Locale.getDefault()).split("\\s+".toRegex()).joinToString(separator = ".")
            return "me.$usernameAsGroupId"
        }

        private fun openSourceFileInEditor(module: Module, path: Path) {
            val newProject = module.project
            StartupManager.getInstance(newProject).runAfterOpened {
                val sourceRoots = module.rootManager.getSourceRoots(false)
                var fileToOpen: VirtualFile? = null

                for (root in sourceRoots) {
                    fileToOpen = root.findFileByRelativePath(path.systemIndependentPath)
                    if (fileToOpen != null) break
                }

                if (fileToOpen != null) {
                    ApplicationManager.getApplication().invokeLater {
                        FileEditorManager.getInstance(newProject).openFile(fileToOpen, true)
                    }
                }
            }
        }
    }


    override val name: String = "Kotlin"

    override fun createStep(parent: NewProjectWizardLanguageStep) = Step(parent)

    class Step(parent: NewProjectWizardLanguageStep) :
        AbstractNewProjectWizardMultiStep<NewProjectWizardLanguageStep, Step>(parent, BuildSystemKotlinNewProjectWizard.EP_NAME),
        NewProjectWizardBuildSystemData,
        NewProjectWizardLanguageData by parent,
        NewProjectWizardSdkData {

        override val self = this
        override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")
        override val comment = JavaUiBundle.message("project.wizard.new.project.kotlin.comment")
        override val commentLink = NewProjectWizardModuleBuilder.MODULE_BUILDER_ID
        override val buildSystemProperty by ::stepProperty
        override val buildSystem by ::step
        override lateinit var sdkComboBox: Cell<JdkComboBox>
        override val sdkProperty = propertyGraph.graphProperty<Sdk?> { null }
        override val sdk by sdkProperty

        override fun setupCommonUI(builder: Panel) {
            with(builder) {
                row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
                    sdkComboBox = sdkComboBox(context, sdkProperty, StdModuleTypes.JAVA.id)
                        .columns(COLUMNS_MEDIUM)
                }
            }
        }
    }
}