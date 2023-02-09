// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard.ui.components

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.entity.SettingValidator
import org.jetbrains.kotlin.tools.projectWizard.core.entity.ValidationResult
import org.jetbrains.kotlin.tools.projectWizard.core.entity.isSpecificError
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingReference
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingType
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.DynamicComponent
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.FocusableComponent
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.customPanel
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.label
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting.IdeaBasedComponentValidator
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

abstract class UIComponent<V : Any>(
    context: Context,
    labelText: @NlsContexts.Label String? = null,
    private val validator: SettingValidator<V>? = null,
    private val onValueUpdate: (V, isByUser: Boolean) -> Unit = { _, _ -> }
) : DynamicComponent(context), FocusableComponent {
    open val alignTarget: JComponent? = null
    private val validationIndicator by lazy(LazyThreadSafetyMode.NONE) {
        if (validator != null)
            IdeaBasedComponentValidator(this, getValidatorTarget())
        else null
    }

    protected abstract val uiComponent: JComponent

    protected open fun getValidatorTarget() = uiComponent

    abstract fun updateUiValue(newValue: V)
    abstract fun getUiValue(): V?

    private var allowEventFiring = true

    protected fun fireValueUpdated(value: V) {
        if (allowEventFiring) {
            onValueUpdate(value, true)
        }
        validate(value)
    }

    protected fun forceValueUpdate(newValue: V) {
        onValueUpdate(newValue, false)
    }

    override fun onInit() {
        super.onInit()
        getUiValue()?.let(::validate)
    }

    final override val component: JComponent by lazy(LazyThreadSafetyMode.NONE) {
        customPanel {
            labelText?.let { add(label("$it:"), BorderLayout.NORTH) }
            add(uiComponent, BorderLayout.CENTER)
        }
    }

    protected fun safeUpdateUi(updater: () -> Unit) {
        val allowEventFiringSaved = allowEventFiring
        allowEventFiring = false
        updater()
        allowEventFiring = allowEventFiringSaved
    }

    open fun validate(value: V) {
        if (validator == null) return
        if (validationIndicator == null) return
        read {
            validationIndicator?.updateValidationState(validator.validate(this, value))
        }
    }

    override fun focusOn() {
        uiComponent.firstNonPanelOrNull()?.requestFocusInWindow()
    }

    override fun navigateTo(error: ValidationResult.ValidationError) {
        val errorInThisComponent = read {
            getUiValue()?.let { validator?.validate?.invoke(this, it)?.isSpecificError(error) } == true
        }
        if (errorInThisComponent) {
            focusOn()
        }
    }
}

fun <V : Any> Reader.valueForSetting(
    uiComponent: UIComponent<V>,
    setting: SettingReference<V, SettingType<V>>
): V? = setting.savedOrDefaultValue ?: uiComponent.getUiValue()

private fun JComponent.firstNonPanelOrNull(): JComponent? {
    if (this !is JPanel) return this

    val nestedComponents = synchronized(treeLock) { components ?: return null }
    for (component in nestedComponents) {
        return component.safeAs<JComponent>()?.firstNonPanelOrNull() ?: continue
    }

    return null
}
