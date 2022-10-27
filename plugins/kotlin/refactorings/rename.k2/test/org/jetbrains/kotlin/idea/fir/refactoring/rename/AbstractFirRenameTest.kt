// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.refactoring.rename

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.idea.refactoring.rename.AbstractRenameTest
import org.jetbrains.kotlin.idea.refactoring.rename.loadTestConfiguration
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractFirRenameTest : AbstractRenameTest() {

    /**
     * Rename tests are not 100% stable ATM, so we only run the tests that will definitely pass.
     *
     * Use this flag locally to find out which tests might be enabled.
     */
    private val onlyRunEnabledTests: Boolean = true

    override fun isFirPlugin(): Boolean = true

    override fun doTest(path: String) {
        val renameObject = loadTestConfiguration(dataFile())
        val testIsEnabledInK2 = renameObject.get("enabledInK2")?.asBoolean == true

        if (!testIsEnabledInK2 && onlyRunEnabledTests) return

        val result = runCatching { super.doTest(path) }
        result.fold(
            onSuccess = { require(testIsEnabledInK2) { "This test passes and should be enabled!" } },
            onFailure = { exception -> if (testIsEnabledInK2) throw exception }
        )
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun findPsiDeclarationToRename(contextFile: KtFile, target: KotlinTarget): PsiElement = allowAnalysisOnEdt {
        analyze(contextFile) {
            when (target) {
                is KotlinTarget.Classifier -> getClassOrObjectSymbolByClassId(target.classId)?.psi!!
                is KotlinTarget.Callable -> {
                    val callableId = target.callableId

                    val scope = callableId.classId
                        ?.let { classId -> getClassOrObjectSymbolByClassId(classId)!!.getMemberScope() }
                        ?: getPackageSymbolIfPackageExists(callableId.packageName)!!.getPackageScope()

                    val callablesOfProperType = scope.getCallableSymbols { it == callableId.callableName }
                        .mapNotNull {
                            when (target.type) {
                                KotlinTarget.CallableType.FUNCTION -> it as? KtFunctionSymbol
                                KotlinTarget.CallableType.PROPERTY -> it as? KtPropertySymbol
                            }
                        }

                    callablesOfProperType.first().psi!!
                }
            }
        }
    }
}