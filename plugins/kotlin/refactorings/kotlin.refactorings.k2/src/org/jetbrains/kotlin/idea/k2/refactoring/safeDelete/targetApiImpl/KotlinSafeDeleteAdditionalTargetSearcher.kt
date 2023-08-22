// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete.targetApiImpl

import com.intellij.refactoring.safeDelete.api.SafeDeleteAdditionalTargetSearcher
import com.intellij.refactoring.safeDelete.api.SafeDeleteAdditionalTargetsSearchParameters
import com.intellij.refactoring.safeDelete.api.SafeDeleteTarget
import com.intellij.refactoring.safeDelete.api.SafeDeleteTargetProvider
import com.intellij.util.Query
import com.intellij.util.mappingNotNull
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinOverridingCallableSearch
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

@Suppress("unused")
class KotlinSafeDeleteAdditionalTargetSearcher : SafeDeleteAdditionalTargetSearcher {
    override fun collectSearchRequest(parameters: SafeDeleteAdditionalTargetsSearchParameters): Query<out SafeDeleteTarget>? {
        val currentTarget = parameters.rootTarget
        if (currentTarget is KotlinSafeDeleteTarget) {
            when (val ktElement = currentTarget.ktElement) {
                is KtParameter -> {
                    val ownerFunction = ktElement.ownerFunction as? KtFunction ?: return null
                    val parameterIndex = ktElement.parameterIndex()
                    return DirectKotlinOverridingCallableSearch.search(ownerFunction)
                        .mappingNotNull {
                            val ktFunction = it as? KtFunction ?: return@mappingNotNull null
                            SafeDeleteTargetProvider.createSafeDeleteTarget(ktFunction.valueParameters[parameterIndex])
                        }
                }

                is KtCallableDeclaration ->
                    return DirectKotlinOverridingCallableSearch.search(ktElement)
                            .mappingNotNull {
                                SafeDeleteTargetProvider.createSafeDeleteTarget(it)
                            }
            }
        }

        return null
    }

}