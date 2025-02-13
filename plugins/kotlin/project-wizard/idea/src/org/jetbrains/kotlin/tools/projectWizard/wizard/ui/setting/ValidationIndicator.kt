// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(UnsafeCastFunction::class)

package org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import org.jetbrains.kotlin.tools.projectWizard.core.entity.ValidationResult
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import javax.swing.JComponent

interface ValidationIndicator {
    fun updateValidationState(newState: ValidationResult)
    val validationState: ValidationResult
}

class IdeaBasedComponentValidator(
    parentDisposable: Disposable,
    private val jComponent: JComponent
) : ValidationIndicator {
    override var validationState: ValidationResult = ValidationResult.OK
        private set

    private val validator = ComponentValidator(parentDisposable).installOn(jComponent)

    override fun updateValidationState(newState: ValidationResult) {
        validationState = newState
        validator.updateInfo(
            newState.safeAs<ValidationResult.ValidationError>()
                ?.messages
                ?.firstOrNull()
                ?.let { ValidationInfo(it, jComponent) }
        )
    }
}