// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard.ui.firstStep

import com.intellij.icons.AllIcons
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.TitledSeparator
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.kotlin.idea.projectWizard.WizardStatsService
import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingReference
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.reference
import org.jetbrains.kotlin.tools.projectWizard.plugins.StructurePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.wizard.IdeWizard
import org.jetbrains.kotlin.tools.projectWizard.wizard.KotlinNewProjectWizardUIBundle
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.*
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting.PathSettingComponent
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting.StringSettingComponent
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting.TitledComponentsList
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting.createSettingComponent
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

class FirstWizardStepComponent(ideWizard: IdeWizard) : WizardStepComponent(ideWizard.context) {
    private val projectSettingsComponent = ProjectSettingsComponent(ideWizard).asSubComponent()

    override val component: JComponent = projectSettingsComponent.component
}

class ProjectSettingsComponent(ideWizard: IdeWizard) : DynamicComponent(ideWizard.context) {
    private val context = ideWizard.context
    private val projectTemplateComponent = ProjectTemplateSettingComponent(context).asSubComponent()
    private val buildSystemSetting = BuildSystemTypeSettingComponent(context).asSubComponent().apply {
        component.addBorder(JBUI.Borders.empty(0, /*left&right*/4))
    }

    private var locationWasUpdatedByHand: Boolean = false
    private var artifactIdWasUpdatedByHand: Boolean = false

    private val buildSystemAdditionalSettingsComponent =
        BuildSystemAdditionalSettingsComponent(
            ideWizard,
            onUserTypeInArtifactId = { artifactIdWasUpdatedByHand = true },
        ).asSubComponent()
    private val jdkComponent = JdkComponent(ideWizard).asSubComponent()

    private val nameAndLocationComponent = TitledComponentsList(
        listOf(
            StructurePlugin.name.reference.createSettingComponent(context),
            StructurePlugin.projectPath.reference.createSettingComponent(context).also {
                (it as? PathSettingComponent)?.onUserType { locationWasUpdatedByHand = true }
            },
            projectTemplateComponent,
            buildSystemSetting,
            jdkComponent
        ),
        context,
        stretchY = true,
        useBigYGap = true,
        xPadding = 0,
        yPadding = 0
    ).asSubComponent()

    override val component: JComponent by lazy(LazyThreadSafetyMode.NONE) {
        panel {
            row {
                cell(nameAndLocationComponent.component)
                    .horizontalAlign(HorizontalAlign.FILL)
                    .resizableColumn()

                bottomGap(BottomGap.SMALL)
            }

            row {
                cell(buildSystemAdditionalSettingsComponent.component)
                    .horizontalAlign(HorizontalAlign.FILL)
                    .resizableColumn()

                bottomGap(BottomGap.SMALL)
            }
        }.addBorder(IdeBorderFactory.createEmptyBorder(JBInsets(20, 20, 20, 20)))
    }

    override fun onValueUpdated(reference: SettingReference<*, *>?) {
        super.onValueUpdated(reference)
        when (reference?.path) {
            StructurePlugin.name.path -> {
                val isNameValid = read { StructurePlugin.name.reference.validate().isOk }
                if (isNameValid) {
                    tryUpdateLocationByProjectName()
                    tryArtifactIdByProjectName()
                }
            }
        }
    }

    private fun tryUpdateLocationByProjectName() {
        if (!locationWasUpdatedByHand) {
            val location = read { StructurePlugin.projectPath.settingValue }
            if (location.parent != null) modify {
                StructurePlugin.projectPath.reference.setValue(location.resolveSibling(StructurePlugin.name.settingValue))
                locationWasUpdatedByHand = false
            }
        }
    }

    private fun tryArtifactIdByProjectName() {
        if (!artifactIdWasUpdatedByHand) modify {
            StructurePlugin.artifactId.reference.setValue(StructurePlugin.name.settingValue)
            artifactIdWasUpdatedByHand = false
        }
    }
}

class BuildSystemAdditionalSettingsComponent(
    ideWizard: IdeWizard,
    onUserTypeInArtifactId: () -> Unit,
) : DynamicComponent(ideWizard.context) {
    private val pomSettingsList = PomSettingsComponent(ideWizard.context, onUserTypeInArtifactId).asSubComponent()

    override fun onValueUpdated(reference: SettingReference<*, *>?) {
        super.onValueUpdated(reference)
        if (reference == BuildSystemPlugin.type.reference) {
            updateBuildSystemComponent()
        }
    }

    override fun onInit() {
        super.onInit()
        updateBuildSystemComponent()
    }

    private fun updateBuildSystemComponent() {
        val buildSystemType = read { BuildSystemPlugin.type.settingValue }
        section.isVisible = buildSystemType != BuildSystemType.Jps
    }

    private val section = HideableSection(
        KotlinNewProjectWizardUIBundle.message("additional.buildsystem.settings.artifact.coordinates"),
        pomSettingsList.component
    )

    override val component: JComponent = section
}

private class PomSettingsComponent(context: Context, onUserTypeInArtifactId: () -> Unit) : TitledComponentsList(
    listOf(
        StructurePlugin.groupId.reference.createSettingComponent(context),
        StructurePlugin.artifactId.reference.createSettingComponent(context).also {
            (it as? StringSettingComponent)?.onUserType(onUserTypeInArtifactId)
        },
        StructurePlugin.version.reference.createSettingComponent(context)
    ),
    context,
    stretchY = true
)

private class JdkComponent(ideWizard: IdeWizard) : TitledComponent(ideWizard.context) {
    private val javaModuleBuilder = JavaModuleBuilder()
    private val jdkComboBox: JdkComboBox
    private val sdksModel: ProjectSdksModel

    init {
        val project = ProjectManager.getInstance().defaultProject

        sdksModel = ProjectSdksModel()
        sdksModel.reset(project)

        jdkComboBox = JdkComboBox(
            project,
            sdksModel,
            Condition(javaModuleBuilder::isSuitableSdkType),
            null,
            null,
            null
        ).apply {
            reloadModel()
            getDefaultJdk()?.let { jdk -> selectedJdk = jdk }

            ideWizard.jdk = selectedJdk
            addActionListener {
                ideWizard.jdk = selectedJdk
                WizardStatsService.logDataOnJdkChanged(ideWizard.context.contextComponents.get())
            }
        }
    }

    private fun getDefaultJdk(): Sdk? {
        val defaultProject = ProjectManagerEx.getInstanceEx().defaultProject
        return ProjectRootManagerEx.getInstanceEx(defaultProject).projectSdk ?: run {
            val sdks = ProjectJdkTable.getInstance().allJdks
                .filter { it.homeDirectory?.isValid == true && javaModuleBuilder.isSuitableSdkType(it.sdkType) }
                .groupBy(Sdk::getSdkType)
                .entries.firstOrNull()
                ?.value?.filterNotNull() ?: return@run null
            sdks.maxWithOrNull(sdks.firstOrNull()?.sdkType?.versionComparator() ?: return@run null)
        }
    }

    override val title: String = KotlinNewProjectWizardUIBundle.message("additional.buildsystem.settings.project.jdk")
    override val component: JComponent = jdkComboBox
}

@Suppress("SpellCheckingInspection")
private class HideableSection(@NlsContexts.Separator text: String, component: JComponent) : BorderLayoutPanel() {
    private val titledSeparator = TitledSeparator(text)
    private val contentPanel = borderPanel {
        addBorder(JBUI.Borders.emptyLeft(20))
        addToCenter(component)
    }
    private var isExpanded = false

    init {
        titledSeparator.label.cursor = Cursor(Cursor.HAND_CURSOR)
        addToTop(titledSeparator)
        addToCenter(contentPanel)
        update(isExpanded)
        titledSeparator.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) = update(!isExpanded)
        })
    }

    private fun update(isExpanded: Boolean) {
        this.isExpanded = isExpanded
        contentPanel.isVisible = isExpanded
        titledSeparator.label.icon = if (isExpanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
    }
}