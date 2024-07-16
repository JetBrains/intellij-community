// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

class ParametersInfo<KotlinType, MutableParameter : IMutableParameter<KotlinType>> {
    var errorMessage: AnalysisResult.ErrorMessage? = null
    val originalRefToParameter = MultiMap.create<KtSimpleNameExpression, MutableParameter>()
    val parameters = LinkedHashSet<MutableParameter>()
    val typeParameters = HashSet<TypeParameter>()
    val nonDenotableTypes = HashSet<KotlinType>()
    val replacementMap = MultiMap.create<KtSimpleNameExpression, IReplacement<KotlinType>>()
}