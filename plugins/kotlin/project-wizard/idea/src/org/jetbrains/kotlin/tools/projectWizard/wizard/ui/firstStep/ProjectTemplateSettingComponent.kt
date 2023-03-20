// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard.ui.firstStep

import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.DropDownSettingType
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingReference
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.reference
import org.jetbrains.kotlin.tools.projectWizard.plugins.projectTemplates.ProjectTemplatesPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.projectTemplates.applyProjectTemplate
import org.jetbrains.kotlin.tools.projectWizard.projectTemplates.ProjectTemplate
import org.jetbrains.kotlin.tools.projectWizard.wizard.OnUserSettingChangeStatisticsLogger
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.*
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting.SettingComponent
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting.ValidationIndicator
import java.awt.Dimension
import javax.swing.JComponent

class ProjectTemplateSettingComponent(
    context: Context
) : SettingComponent<ProjectTemplate, DropDownSettingType<ProjectTemplate>>(
    ProjectTemplatesPlugin.template.reference,
    context
) {
    override val validationIndicator: ValidationIndicator? get() = null
    private val templateDescriptionComponent = TemplateDescriptionComponent().asSubComponent()

    private val templateGroups = setting.type.values
        .groupBy { it.projectKind }
        .map { (group, templates) ->
            ListWithSeparators.ListGroup(group.text, templates)
        }

    private val list = ListWithSeparators(
        templateGroups,
        render = { value ->
            icon = value.icon
            append(value.title)
            value.projectKind.message?.let { message ->
                append(" ")
                append(message, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        },
        onValueSelected = { newValue ->
            newValue?.let {
                OnUserSettingChangeStatisticsLogger.logSettingValueChangedByUser(
                    context.contextComponents.get(),
                    ProjectTemplatesPlugin.template.path,
                    it
                )
            }
            value = newValue
        }
    )

    override val alignment: TitleComponentAlignment
        get() = TitleComponentAlignment.AlignFormTopWithPadding(4)

    private val scrollPane = ScrollPaneFactory.createScrollPane(list)

    override val component: JComponent = borderPanel {
        addToCenter(borderPanel { addToCenter(scrollPane) }.addBorder(JBUI.Borders.empty(0,/*left*/ 3, 0, /*right*/ 3)))
        addToBottom(templateDescriptionComponent.component.addBorder(JBUI.Borders.empty(/*top*/8,/*left*/ 3, 0, 0)))
    }

    private fun applySelectedTemplate() = modify {
        value?.let(::applyProjectTemplate)
    }

    override fun onValueUpdated(reference: SettingReference<*, *>?) {
        super.onValueUpdated(reference)
        if (reference == ProjectTemplatesPlugin.template.reference) {
            applySelectedTemplate()
            updateHint()
        }
    }

    override fun onInit() {
        super.onInit()
        if (setting.type.values.isNotEmpty()) {
            list.selectedIndex = 0
            value = setting.type.values.firstOrNull()
            applySelectedTemplate()
            updateHint()
        }
    }

    private fun updateHint() {
        value?.let { template ->
            list.setSelectedValue(template, true)
            templateDescriptionComponent.setTemplate(template)
        }
    }
}

class TemplateDescriptionComponent : Component() {
    private val descriptionLabel = CommentLabel().apply {
        preferredSize = Dimension(preferredSize.width, 45)
    }

    fun setTemplate(template: ProjectTemplate) {
        descriptionLabel.text = template.description.asHtml()
    }

    override val component: JComponent = descriptionLabel
}