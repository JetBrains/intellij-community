// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.introduceTypeAlias.ui

import org.jetbrains.kotlin.idea.k2.refactoring.introduceTypeAlias.TypeParameter
import org.jetbrains.kotlin.idea.refactoring.introduce.ui.AbstractParameterTablePanel

open class IntroduceTypeAliasParameterTablePanel :
    AbstractParameterTablePanel<TypeParameter, IntroduceTypeAliasParameterTablePanel.TypeParameterInfo>() {
    class TypeParameterInfo(
        originalParameter: TypeParameter
    ) : AbstractParameterTablePanel.AbstractParameterInfo<TypeParameter>(originalParameter) {
        init {
            name = originalParameter.name
        }

        override fun toParameter() = originalParameter.copy(name)
    }

    fun init(parameters: List<TypeParameter>) {
        parameterInfos = parameters.mapTo(ArrayList(), ::TypeParameterInfo)
        super.init()
    }

    override fun isCheckMarkColumnEditable() = false

    val selectedTypeParameterInfos: List<TypeParameterInfo>
        get() = parameterInfos.filter { it.isEnabled }

    val selectedTypeParameters: List<TypeParameter>
        get() = selectedTypeParameterInfos.map { it.toParameter() }
}