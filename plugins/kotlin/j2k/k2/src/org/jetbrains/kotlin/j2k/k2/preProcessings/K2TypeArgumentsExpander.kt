// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2.preProcessings

import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeArgumentsFix
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceParameterList
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.infos.MethodCandidateInfo
import org.jetbrains.kotlin.j2k.J2kPreprocessorExtension
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class K2TypeArgumentsExpander: J2kPreprocessorExtension {
    override suspend fun processFiles(
        project: Project,
        files: List<PsiJavaFile>
    ) {
        for (file in files) {
            val updates = readAction { collectPsiUpdates(file) }
            for ((original, replacement) in updates) {
                edtWriteAction {
                    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled {
                        original.replace(replacement)
                    }
                }
            }
        }
    }

    private fun collectPsiUpdates(file: PsiJavaFile): List<PsiTypeArgumentsUpdate> = buildList {
        collectTypeArgumentUpdates(file) { originalTypeArgumentList, replacementTypeArgumentList ->
            add(PsiTypeArgumentsUpdate(originalTypeArgumentList, replacementTypeArgumentList))
        }
    }.sortedByDescending { it.original.textRange.startOffset }

    private inline fun collectTypeArgumentUpdates(
        file: PsiJavaFile,
        crossinline
        onUpdate: (originalTypeArgumentList: PsiReferenceParameterList, replacementTypeArgumentList: PsiReferenceParameterList) -> Unit,
    ) {
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)

                val originalTypeArgumentList = expression.typeArgumentList
                val replacementTypeArgumentList = expression.findReplacementTypeArgumentList() ?: return
                onUpdate(originalTypeArgumentList, replacementTypeArgumentList)
            }
        })
    }

    private fun PsiMethodCallExpression.findReplacementTypeArgumentList(): PsiReferenceParameterList? {
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

    private data class PsiTypeArgumentsUpdate(
        val original: PsiReferenceParameterList,
        val replacement: PsiReferenceParameterList,
    )
}
