// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeArgumentsFix
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceParameterList
import com.intellij.psi.infos.MethodCandidateInfo
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Returns the type arguments that Java infers for the calls in [file] that have no explicit type arguments.
 * J2K converts these type arguments like explicit ones, but does not write them into [file].
 */
internal fun collectInferredTypeArguments(file: PsiJavaFile): Map<PsiMethodCallExpression, PsiReferenceParameterList> {
    val typeArguments = HashMap<PsiMethodCallExpression, PsiReferenceParameterList>()
    file.accept(object : JavaRecursiveElementWalkingVisitor() {
        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
            super.visitMethodCallExpression(expression)
            expression.inferredTypeArgumentList()?.let { typeArguments[expression] = it }
        }
    })
    return typeArguments
}

private fun PsiMethodCallExpression.inferredTypeArgumentList(): PsiReferenceParameterList? {
    if (typeArguments.isNotEmpty()) return null

    val resolveResult = resolveMethodGenerics()
    if (resolveResult is MethodCandidateInfo && resolveResult.isApplicable) {
        val method = resolveResult.element
        if (method.isConstructor || !method.hasTypeParameters()) return null

        // Avoid incorrect type arguments insertion that will lead to red code
        QualifiedNameProviderUtil.getQualifiedName(method)?.let { methodName ->
            if (methodName.startsWith("java.util.stream.Stream#collect") ||
                methodName.startsWith("java.util.stream.Collectors")
            ) {
                return null
            }
        }
    }

    return AddTypeArgumentsFix.addTypeArguments(this, null, false)
        ?.safeAs<PsiMethodCallExpression>()
        ?.typeArgumentList
}
