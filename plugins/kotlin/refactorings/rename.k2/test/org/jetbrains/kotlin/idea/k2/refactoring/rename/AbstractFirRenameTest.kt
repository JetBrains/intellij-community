// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaDeclarationContainerSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getSymbolContainingMemberDeclarations
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.refactoring.rename.AbstractRenameTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractFirRenameTest : AbstractRenameTest() {

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun doTest(path: String) {
        allowAnalysisOnEdt { super.doTest(path) }
    }

    override fun checkForUnexpectedErrors(ktFile: KtFile) {}

    override fun findPsiDeclarationToRename(contextFile: KtFile, target: KotlinTarget): PsiElement = analyze(contextFile) {
        fun getContainingMemberSymbol(classId: ClassId): KaDeclarationContainerSymbol {
            findClass(classId)?.let { return it }
            val parentSymbol = findClass(classId.parentClassId!!)!!

            // The function supports getting a `KaEnumEntrySymbol`'s initializer via the enum entry's "class ID". Despite not being 100%
            // semantically correct in FIR (enum entries aren't classes), it simplifies referring to the initializing object.
            val declarationSymbol = parentSymbol.staticDeclaredMemberScope.callables(classId.shortClassName).first()
            return declarationSymbol.getSymbolContainingMemberDeclarations() ?:
                error("Unexpected declaration symbol `$classId` of type `${declarationSymbol.javaClass.simpleName}`.")
        }

        when (target) {
            is KotlinTarget.Classifier -> getContainingMemberSymbol(target.classId).psi!!

            is KotlinTarget.Callable -> {
                val callableId = target.callableId
                val scope = callableId.classId
                    ?.let { classId -> getContainingMemberSymbol(classId).memberScope }
                    ?: findPackage(callableId.packageName)!!.packageScope

                val callablesOfProperType = scope.callables(callableId.callableName)
                    .mapNotNull {
                        when (target.type) {
                            KotlinTarget.CallableType.FUNCTION -> it as? KaNamedFunctionSymbol
                            KotlinTarget.CallableType.PROPERTY -> it as? KaPropertySymbol
                        }
                    }

                callablesOfProperType.first().psi!!
            }

            is KotlinTarget.EnumEntry -> {
                val callableId = target.callableId
                val containingScope = getContainingMemberSymbol(callableId.classId!!).staticDeclaredMemberScope
                containingScope.callables(callableId.callableName).singleOrNull()?.psi!!
            }
        }
    }
}
