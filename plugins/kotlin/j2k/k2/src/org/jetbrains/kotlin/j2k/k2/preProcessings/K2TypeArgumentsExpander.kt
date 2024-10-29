// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2.preProcessings

import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeArgumentsFix
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceParameterList
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.infos.MethodCandidateInfo
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.kotlin.j2k.J2kPreprocessorExtension
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class K2TypeArgumentsExpander: J2kPreprocessorExtension {
    override suspend fun processFiles(
        project: Project,
        files: List<PsiJavaFile>
    ) {


        for (file in files) {
            val map = mutableMapOf<PsiMethodCallExpression, PsiReferenceParameterList>()
            readAction {
                file.accept(object : JavaRecursiveElementWalkingVisitor() {
                    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                        super.visitMethodCallExpression(expression)

                        if (expression.typeArguments.isNotEmpty()) return

                        val resolveResult = expression.resolveMethodGenerics()
                        if (resolveResult is MethodCandidateInfo && resolveResult.isApplicable) {
                            val method = resolveResult.element
                            if (method.isConstructor || !method.hasTypeParameters()) return

                            // Avoid incorrect type arguments insertion that will lead to red code
                            QualifiedNameProviderUtil.getQualifiedName(method)?.let { methodName ->
                                if (methodName.startsWith("java.util.stream.Stream#collect") ||
                                    methodName.startsWith("java.util.stream.Collectors")
                                ) {
                                    return
                                }
                            }
                        }

                        val typeArgumentList = AddTypeArgumentsFix.addTypeArguments(expression, null, false)
                            ?.safeAs<PsiMethodCallExpression>()
                            ?.typeArgumentList
                        if (typeArgumentList != null) {
                            map.put(expression, typeArgumentList)
                        }
                    }
                })
            }
            val sortedEntries = readAction { map.entries.sortedBy { -it.key.textRange.startOffset } }
            for (entry in sortedEntries) {
                writeAction {
                    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled {
                        entry.key.typeArgumentList.replace(entry.value)
                    }
                }
            }
        }
    }

}