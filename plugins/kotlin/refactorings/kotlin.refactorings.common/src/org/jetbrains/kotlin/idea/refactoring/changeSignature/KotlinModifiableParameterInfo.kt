// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ParameterInfo
import org.jetbrains.kotlin.psi.KtExpression

interface KotlinModifiableParameterInfo : ParameterInfo {
    var valOrVar: KotlinValVar
    fun setType(newType: String)
    val isNewParameter: Boolean
    var defaultValueForCall: KtExpression?
    var defaultValueAsDefaultParameter: Boolean
    val defaultValue: KtExpression?
    val originalIndex: Int
    var isContextParameter: Boolean
}