// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard.ui.firstStep

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.project.DumbAware
import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.entity.ValidationResult
import org.jetbrains.kotlin.tools.projectWizard.core.entity.isSpecificError
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.DropDownSettingType
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.reference
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.wizard.OnUserSettingChangeStatisticsLogger
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.TitleComponentAlignment
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting.IdeaBasedComponentValidator
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting.SettingComponent
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting.ValidationIndicator
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.awt.Dimension
import java.awt.Insets
import java.util.function.Supplier
import javax.swing.JComponent

class BuildSystemTypeSettingComponent(
    context: Context
) : SettingComponent<BuildSystemType, DropDownSettingType<BuildSystemType>>(
    BuildSystemPlugin.type.reference,
    context
) {

    private val toolbar by lazy(LazyThreadSafetyMode.NONE) {
        val buildSystemTypes = read { setting.type.values.filter { setting.type.filter(this, reference, it) } }
        val actionGroup = DefaultActionGroup(buildSystemTypes.map(::BuildSystemTypeAction))
        val buildSystemToolbar = BuildSystemToolbar(ActionPlaces.NEW_PROJECT_WIZARD, actionGroup, true)
        buildSystemToolbar.also { it.targetComponent = null }
    }

    override val alignment: TitleComponentAlignment
        get() = TitleComponentAlignment.AlignFormTopWithPadding(6)

    override val component: JComponent by lazy(LazyThreadSafetyMode.NONE) {
        toolbar
    }

    override val validationIndicator: ValidationIndicator =
        IdeaBasedComponentValidator(this, component)

    override fun navigateTo(error: ValidationResult.ValidationError) {
        if (validationIndicator.validationState.isSpecificError(error)) {
            component.requestFocus()
        }
    }

    private fun validateBuildSystem(buildSystem: BuildSystemType) = read {
        setting.validator.validate(this, buildSystem)
    }

    private inner class BuildSystemTypeAction(
        val buildSystemType: BuildSystemType
    ) : ToggleAction(buildSystemType.text, null, null), DumbAware {
        override fun isSelected(e: AnActionEvent): Boolean = value == buildSystemType

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (state) {
                value = buildSystemType
                OnUserSettingChangeStatisticsLogger.logSettingValueChangedByUser(
                    context.contextComponents.get(),
                    BuildSystemPlugin.type.path,
                    buildSystemType
                )
            }
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            val validationResult = validateBuildSystem(buildSystemType)
            e.presentation.isEnabled = validationResult.isOk
            e.presentation.description = validationResult.safeAs<ValidationResult.ValidationError>()?.messages?.firstOrNull()
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    private inner class BuildSystemToolbar(
        place: String,
        actionGroup: ActionGroup,
        horizontal: Boolean
    ) : ActionToolbarImplWrapper(place, actionGroup, horizontal) {
        init {
            layoutPolicy = ActionToolbar.WRAP_LAYOUT_POLICY
        }

        override fun createToolbarButton(
            action: AnAction,
            look: ActionButtonLook?,
            place: String,
            presentation: Presentation,
            minimumSize: Supplier<out Dimension>
        ): ActionButton = BuildSystemChooseButton(action as BuildSystemTypeAction, presentation, place, minimumSize)
    }

    private inner class BuildSystemChooseButton(
        action: BuildSystemTypeAction,
        presentation: Presentation,
        place: String,
        minimumSize: Supplier<out Dimension>
    ) : ActionButtonWithText(action, presentation, place, minimumSize) {
        override fun getInsets(): Insets = super.getInsets().apply {
            right += left
            left = 0
        }

        override fun getPreferredSize(): Dimension {
            val old = super.getPreferredSize()
            return Dimension(old.width + LEFT_RIGHT_PADDING * 2, old.height + TOP_BOTTOM_PADDING * 2)
        }
    }

    companion object {
        private const val LEFT_RIGHT_PADDING = 6
        private const val TOP_BOTTOM_PADDING = 2
    }
}