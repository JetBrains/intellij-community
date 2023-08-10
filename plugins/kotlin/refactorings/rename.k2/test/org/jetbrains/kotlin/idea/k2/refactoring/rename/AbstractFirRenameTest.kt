// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtPossiblyNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithMembers
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.refactoring.rename.AbstractRenameTest
import org.jetbrains.kotlin.idea.refactoring.rename.loadTestConfiguration
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractFirRenameTest : AbstractRenameTest() {

    /**
     * Rename tests are not 100% stable ATM, so we only run the tests that will definitely pass.
     *
     * Use this flag locally to find out which tests might be enabled.
     */
    private val onlyRunEnabledTests: Boolean = true

    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun doTest(path: String) {
        val renameObject = loadTestConfiguration(dataFile())
        val testIsEnabledInK2 = renameObject.get("enabledInK2")?.asBoolean == true

        if (!testIsEnabledInK2 && onlyRunEnabledTests) return

        val result = allowAnalysisOnEdt { runCatching { super.doTest(path) } }
        result.fold(
            onSuccess = { require(testIsEnabledInK2) { "This test passes and should be enabled!" } },
            onFailure = { exception -> if (testIsEnabledInK2) throw exception }
        )
    }

    override fun findPsiDeclarationToRename(contextFile: KtFile, target: KotlinTarget): PsiElement = analyze(contextFile) {
        fun getContainingMemberSymbol(classId: ClassId): KtSymbolWithMembers {
            getClassOrObjectSymbolByClassId(classId)?.let { return it }
            val parentSymbol = getClassOrObjectSymbolByClassId(classId.parentClassId!!)!!
            return parentSymbol.getDeclaredMemberScope().getAllSymbols().first { (it as? KtPossiblyNamedSymbol)?.name == classId.shortClassName } as KtSymbolWithMembers
        }

        when (target) {
            is KotlinTarget.Classifier -> getContainingMemberSymbol(target.classId).psi!!
            is KotlinTarget.Callable -> {
                val callableId = target.callableId
                val scope = callableId.classId
                    ?.let { classId -> getContainingMemberSymbol(classId).getMemberScope() }
                    ?: getPackageSymbolIfPackageExists(callableId.packageName)!!.getPackageScope()

                val callablesOfProperType = scope.getCallableSymbols(callableId.callableName)
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