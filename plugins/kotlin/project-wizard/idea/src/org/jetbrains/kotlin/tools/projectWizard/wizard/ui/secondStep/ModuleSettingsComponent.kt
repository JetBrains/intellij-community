// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard.ui.secondStep

import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.idea.projectWizard.WizardStatsService.UiEditorUsageStats
import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.entity.StringValidators
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingReference
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.CommonTargetConfigurator
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.getConfiguratorSettings
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.KotlinPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.plugins.templates.TemplatesPlugin
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module.Companion.ALLOWED_SPECIAL_CHARS_IN_MODULE_NAMES
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.isRootModule
import org.jetbrains.kotlin.tools.projectWizard.templates.Template
import org.jetbrains.kotlin.tools.projectWizard.templates.settings
import org.jetbrains.kotlin.tools.projectWizard.wizard.KotlinNewProjectWizardUIBundle
import org.jetbrains.kotlin.tools.projectWizard.wizard.OnUserSettingChangeStatisticsLogger
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.*
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.components.DropDownComponent
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.components.TextFieldComponent
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting.TitledComponentsList
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting.createSettingComponent
import javax.swing.JComponent

class ModuleSettingsComponent(
    private val context: Context,
    private val uiEditorUsagesStats: UiEditorUsageStats
) : DynamicComponent(context) {
    private val settingsList = TitledComponentsList(emptyList(), context).asSubComponent()
    private val moduleDependenciesComponent = ModuleDependenciesComponent(context)

    override val component: JComponent = settingsList.component

    var module: Module? = null
        set(value) {
            field = value
            if (value != null) {
                updateModule(value)
            }
        }

    @OptIn(ExperimentalStdlibApi::class)
    private fun updateModule(module: Module) {
        moduleDependenciesComponent.module = module
        val moduleSettingComponents = buildList {
            add(ModuleNameComponent(context, module))
            createTemplatesListComponentForModule(module)?.let(::add)
            addAll(module.getConfiguratorSettings().map { it.createSettingComponent(context) })
            module.template?.let { template ->
                addAll(template.settings(module).map { it.createSettingComponent(context) })
            }
            add(moduleDependenciesComponent)
        }

        settingsList.setComponents(moduleSettingComponents)
    }

    private fun createTemplatesListComponentForModule(module: Module): ModuleTemplateComponent? {
        val templates = read { availableTemplatesFor(module) }
        if (templates.isEmpty()) return null

        assert(templates.all { it.isPermittedForModule(module)}) {
            "Templates available for the module contain non-permitted one: templates=$templates, module=$module"
        }

        // we don't display the component for a single template matching module's default one (nothing to choose from)
        if (templates.size == 1 && templates.first() == module.template) return null

        return ModuleTemplateComponent(context, module, templates, uiEditorUsagesStats) {
            updateModule(module)
            component.updateUI()
        }
    }
}

private class ModuleNameComponent(context: Context, private val module: Module) : TitledComponent(context) {
    private val textField = TextFieldComponent(
        context,
        labelText = null,
        initialValue = module.name,
        validator = validateModuleName
    ) { value, _ ->
        module.name = value
        context.write { eventManager.fireListeners(null) }
    }.asSubComponent()

    override val component: JComponent
        get() = textField.component

    override val title: String = KotlinNewProjectWizardUIBundle.message("module.settings.name")

    override fun shouldBeShown(): Boolean {
        val isSingleRootMode = read { KotlinPlugin.modules.settingValue }.size == 1
        return super.shouldBeShown() && !(isSingleRootMode && module.isRootModule) && (module.configurator != CommonTargetConfigurator)
    }

    companion object {
        private val validateModuleName =
            StringValidators.shouldNotBeBlank(KotlinNewProjectWizardUIBundle.message("module.settings.name.module.name")) and
                    StringValidators.shouldBeValidIdentifier(
                        KotlinNewProjectWizardUIBundle.message("module.settings.name.module.name"),
                        ALLOWED_SPECIAL_CHARS_IN_MODULE_NAMES
                    )
    }
}

private class ModuleTemplateComponent(
    context: Context,
    private val module: Module,
    templates: List<Template>,
    uiEditorUsagesStats: UiEditorUsageStats,
    onTemplateChanged: () -> Unit
) : TitledComponent(context) {

    init {
        if (module.template == null) {
            module.template = templates.firstOrNull()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private val dropDown = DropDownComponent(
        context,
        initialValues = templates,
        initiallySelectedValue = module.template,
        labelText = null,
    ) { value, isByUser ->
        if (isByUser) {
            OnUserSettingChangeStatisticsLogger.logSettingValueChangedByUser(context.contextComponents.get(), "module.template", value)
        }
        module.template = value.takeIf { it != NoneTemplate }
        uiEditorUsagesStats.moduleTemplateChanged++
        changeTemplateDescription(module.template)
        onTemplateChanged()
    }.asSubComponent()

    override val alignment: TitleComponentAlignment
        get() = TitleComponentAlignment.AlignAgainstSpecificComponent(dropDown.component)

    private val templateDescriptionLabel = CommentLabel().apply {
        addBorder(JBUI.Borders.empty(2, 4))
    }

    override fun onInit() {
        super.onInit()
        changeTemplateDescription(module.template)
    }

    private fun changeTemplateDescription(template: Template?) {
        templateDescriptionLabel.text = template?.description?.asHtml()
        templateDescriptionLabel.isVisible = template?.description != null
    }

    override val component = borderPanel {
        addToCenter(dropDown.component)
        addToBottom(templateDescriptionLabel)
    }

    override fun onValueUpdated(reference: SettingReference<*, *>?) {
        super.onValueUpdated(reference)
        dropDown.filterValues()
    }

    override val title: String = KotlinNewProjectWizardUIBundle.message("module.settings.template")
}

private object NoneTemplate : Template() {
    override val title = KotlinNewProjectWizardUIBundle.message("module.settings.template.none")
    override val description: String = ""
    override fun isApplicableTo(module: Module, projectKind: ProjectKind, reader: Reader): Boolean = true

    override val id: String = "none"
}

fun Reader.availableTemplatesFor(module: Module) =
    TemplatesPlugin.templates.propertyValue.values.filter { template ->
        template.isSupportedByModuleType(module, KotlinPlugin.projectKind.settingValue, this)
    }


