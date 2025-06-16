// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.ui.validation.validationErrorIf
import com.jetbrains.python.PyBundle


/**
 * Kotlin DSL UI validator that makes sure no [deniedChars] are used
 */
fun deniedCharsValidation(deniedChars: Regex): DialogValidation.WithParameter<() -> String> =
  validationErrorIf(PyBundle.message("validation.invalid.name")) {
    deniedChars.find(it) != null
  }