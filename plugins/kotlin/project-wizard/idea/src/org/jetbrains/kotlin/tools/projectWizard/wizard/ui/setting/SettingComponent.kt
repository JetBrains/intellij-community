// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting

import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.Setting
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingReference
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingType
import org.jetbrains.kotlin.tools.projectWizard.wizard.OnUserSettingChangeStatisticsLogger
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.TitledComponent
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.FocusableComponent

abstract class SettingComponent<V : Any, T : SettingType<V>>(
    val reference: SettingReference<V, T>,
    val context: Context
) : TitledComponent(context), FocusableComponent {
    var value: V?
        get() = reference.value
        set(value) {
            reference.value = value
        }

    val setting: Setting<V, T>
        get() = read { reference.setting }

    abstract val validationIndicator: ValidationIndicator?
    override val title: String? get() = setting.title
    override val tooltipText: String? = setting.tooltipText

    override fun onInit() {
        super.onInit()
        updateValidationState()
    }

    override fun onValueUpdated(reference: SettingReference<*, *>?) {
        component.isVisible = read { setting.isActive(this) }
        updateValidationState()
    }

    private fun updateValidationState() {
        val value = value
        if (validationIndicator != null && value != null) read {
            validationIndicator!!.updateValidationState(setting.validator.validate(this, value))
        }
    }
}

fun <T : Any> SettingComponent<T, *>.handleValueUpdate() = { newValue: T, isUpdatedByUser: Boolean ->
    value = newValue
    if (isUpdatedByUser) {
        OnUserSettingChangeStatisticsLogger.logSettingValueChangedByUser(context.contextComponents.get(), reference, newValue)
    }
}