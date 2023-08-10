// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard.ui.components

import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.entity.SettingValidator
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.componentWithCommentAtBottom
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.textField
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JComponent

class TextFieldComponent(
    context: Context,
    labelText: @NlsContexts.Label String? = null,
    description: @NlsContexts.Label String? = null,
    initialValue: String? = null,
    validator: SettingValidator<String>? = null,
    onValueUpdate: (String, isByUser: Boolean) -> Unit = { _, _ -> }
) : UIComponent<String>(
    context,
    labelText,
    validator,
    onValueUpdate
) {
    private var isDisabled: Boolean = false
    private var cachedValueWhenDisabled: String? = null

    @Suppress("HardCodedStringLiteral")
    private val textField = textField(initialValue.orEmpty(), ::fireValueUpdated)

    override val alignTarget: JComponent get() = textField

    override val uiComponent = componentWithCommentAtBottom(textField, description)

    override fun updateUiValue(newValue: String) = safeUpdateUi {
        textField.text = newValue
    }

    fun onUserType(action: () -> Unit) {
        textField.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent?) = action()
        })
    }

    fun disable(@Nls message: String) {
        cachedValueWhenDisabled = getUiValue()
        textField.isEditable = false
        textField.foreground = UIUtil.getLabelDisabledForeground()
        isDisabled = true
        updateUiValue(message)
    }

    override fun validate(value: String) {
        if (isDisabled) return
        super.validate(value)
    }

    override fun getUiValue(): String = cachedValueWhenDisabled ?: textField.text
}