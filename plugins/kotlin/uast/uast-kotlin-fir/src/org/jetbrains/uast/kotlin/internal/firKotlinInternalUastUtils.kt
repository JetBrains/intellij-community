// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.frontend.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.frontend.api.calls.KtCall
import org.jetbrains.kotlin.idea.frontend.api.types.KtClassErrorType
import org.jetbrains.kotlin.idea.frontend.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.idea.frontend.api.types.KtType
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.kotlin.FirKotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.lz
import java.lang.IllegalStateException

val firKotlinUastPlugin: FirKotlinUastLanguagePlugin by lz {
    UastLanguagePlugin.getInstances().single { it.language == KotlinLanguage.INSTANCE } as FirKotlinUastLanguagePlugin?
        ?: FirKotlinUastLanguagePlugin()
}

internal fun KtAnalysisSession.toPsiClass(ktType: KtType, context: KtElement): PsiClass? {
    return PsiTypesUtil.getPsiClass(toPsiType(ktType, context))
}

internal fun KtAnalysisSession.toPsiMethod(ktCall: KtCall): PsiMethod? {
    if (ktCall.isErrorCall) return null
    val psi = ktCall.targetFunction.candidates.singleOrNull()?.psi ?: return null
    try {
        return psi.getRepresentativeLightMethod()
    } catch (e: IllegalStateException) {
        // TODO: Creating FirModuleResolveState is not yet supported for LibrarySourceInfo(libraryName=myLibrary)
        //  this happens while destructuring a variable via Pair casting (testDestructuringDeclaration).
        return null
    }
}

internal fun KtAnalysisSession.toPsiType(ktType: KtType, context: KtElement): PsiType {
    if (ktType is KtNonErrorClassType && ktType.typeArguments.isEmpty()) {
        // TODO: unclear when we need boxed type.
        val psiType = when (ktType.classId) {
            StandardClassIds.Int -> PsiType.INT
            StandardClassIds.Long -> PsiType.LONG
            StandardClassIds.Short -> PsiType.SHORT
            StandardClassIds.Boolean -> PsiType.BOOLEAN
            StandardClassIds.Byte -> PsiType.BYTE
            StandardClassIds.Char -> PsiType.CHAR
            StandardClassIds.Double -> PsiType.DOUBLE
            StandardClassIds.Float -> PsiType.FLOAT
            StandardClassIds.Unit -> {
                if (context is KtNamedFunction) PsiType.VOID else null
            }
            StandardClassIds.String -> PsiType.getJavaLangString(context.manager, context.resolveScope)
            else -> null
        }
        if (psiType != null) return psiType
    }
    return ktType.asPsiType(context, TypeMappingMode.DEFAULT_UAST) ?: UastErrorType
}

internal fun KtAnalysisSession.nullability(ktType: KtType?): TypeNullability? {
    if (ktType == null) return null
    if (ktType is KtClassErrorType) return null
    return if (ktType.canBeNull) TypeNullability.NULLABLE else TypeNullability.NOT_NULL
}
