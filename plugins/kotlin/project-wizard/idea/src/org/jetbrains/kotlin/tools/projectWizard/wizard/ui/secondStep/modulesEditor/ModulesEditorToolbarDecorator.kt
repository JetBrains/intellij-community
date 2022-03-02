// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard.ui.secondStep.modulesEditor

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.*
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.withAllSubModules
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.ModuleKind
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Sourceset
import org.jetbrains.kotlin.tools.projectWizard.wizard.KotlinNewProjectWizardUIBundle
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.createPanelWithPopupHandler
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.*
import javax.swing.JComponent

class ModulesEditorToolbarDecorator(
    private val tree: ModulesEditorTree,
    private val moduleCreator: NewModuleCreator,
    private val model: TargetsModel,
    private val getModules: () -> List<Module>,
    private val isMultiplatformProject: () -> Boolean
) {
    private val toolbarDecorator: ToolbarDecorator = ToolbarDecorator.createDecorator(tree).apply {
        setToolbarPosition(ActionToolbarPosition.TOP)
        setAddAction { button ->
            val allModules = getModules().withAllSubModules(includeSourcesets = false)
            val target = tree.selectedSettingItem?.safeAs<Module>()
            val isRootModule = target == null
            val popup = moduleCreator.create(
                target = tree.selectedSettingItem as? Module,
                allowMultiplatform = isMultiplatformProject()
                        && isRootModule
                        && allModules.none { it.configurator == MppModuleConfigurator },
                allowSinglePlatformJsBrowser = allowSinglePlatformJs(
                    configurator = BrowserJsSinglePlatformModuleConfigurator,
                    allModules = allModules,
                    isRootModule = isRootModule
                ),
                allowSinglePlatformJsNode = allowSinglePlatformJs(
                    configurator = NodeJsSinglePlatformModuleConfigurator,
                    allModules = allModules,
                    isRootModule = isRootModule
                ),
                allowAndroid = isRootModule
                        && isMultiplatformProject()
                        && allModules.none { it.configurator == AndroidSinglePlatformModuleConfigurator },
                allowIos = isRootModule
                        && isMultiplatformProject()
                        && allModules.none { it.configurator == IOSSinglePlatformModuleConfigurator },
                allModules = allModules,
                createModule = model::add
            )
            popup?.show(button.preferredPopupPoint!!)
        }
        setAddActionUpdater { event ->
            event.presentation.apply {
                isEnabled = when (val selected = tree.selectedSettingItem) {
                    is Module -> selected.configurator.canContainSubModules
                    is Sourceset -> false
                    null -> true
                    else -> false
                }
                val moduleKindTextToAdd = when (tree.selectedSettingItem?.safeAs<Module>()?.kind) {
                    ModuleKind.multiplatform -> KotlinNewProjectWizardBundle.message("module.kind.target")
                    ModuleKind.singlePlatformJvm -> KotlinNewProjectWizardBundle.message("module.kind.module")
                    ModuleKind.singlePlatformJsBrowser -> KotlinNewProjectWizardBundle.message("module.kind.js.browser.module")
                    ModuleKind.singlePlatformJsNode -> KotlinNewProjectWizardBundle.message("module.kind.js.node.module")
                    ModuleKind.singlePlatformAndroid -> KotlinNewProjectWizardBundle.message("module.kind.android.module")
                    ModuleKind.target -> ""
                    null -> ""
                }

                text = KotlinNewProjectWizardUIBundle.message("editor.modules.add", moduleKindTextToAdd.capitalize(Locale.US))
            }
            event.presentation.isEnabled
        }

        setRemoveAction { model.removeSelected() }
        setRemoveActionUpdater { event ->
            event.presentation.apply {
                isEnabled = tree.selectedSettingItem is Module
                text = KotlinNewProjectWizardUIBundle.message(
                    "editor.modules.remove.tooltip",
                    selectedModuleKindText?.let { " ${it.capitalize(Locale.US)}" }.orEmpty()
                )
            }
            event.presentation.isEnabled
        }

        setMoveDownAction(null)
        setMoveUpAction(null)
    }

    private fun allowSinglePlatformJs(
        configurator: JsSinglePlatformModuleConfigurator,
        allModules: List<Module>,
        isRootModule: Boolean
    ) =
        isMultiplatformProject()
                && isRootModule
                && allModules.none { it.configurator == configurator }

    private val selectedModule
        get() = tree.selectedSettingItem.safeAs<Module>()

    private val selectedModuleKindText
        get() = selectedModule?.kindText

    fun createToolPanel(): JComponent = toolbarDecorator
        .createPanelWithPopupHandler(tree)
        .apply {
            border = null
        }
}

private val Module.kindText
    get() = when (kind) {
        ModuleKind.multiplatform -> KotlinNewProjectWizardBundle.message("module.kind.module")
        ModuleKind.singlePlatformJvm -> KotlinNewProjectWizardBundle.message("module.kind.module")
        ModuleKind.singlePlatformJsBrowser -> KotlinNewProjectWizardBundle.message("module.kind.module")
        ModuleKind.singlePlatformJsNode -> KotlinNewProjectWizardBundle.message("module.kind.module")
        ModuleKind.singlePlatformAndroid -> KotlinNewProjectWizardBundle.message("module.kind.android.module")
        ModuleKind.target -> KotlinNewProjectWizardBundle.message("module.kind.target")
    }
