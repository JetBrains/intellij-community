// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.base.psi.childrenDfsSequence
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.util.graph.DirectedGraph
import org.jetbrains.kotlin.util.graph.sortTopologically

object ReorderParametersFixFactory {

    val unInitializedParameter = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UninitializedParameter ->
        createQuickFix(diagnostic)
    }

    context(_: KaSession)
    private fun createQuickFix(diagnostic: KaFirDiagnostic<*>): List<ReorderParametersFix> {
        val function: KtNamedFunction = diagnostic.psi.parentOfType(withSelf = true) ?: return emptyList()
        val functionSymbol = function.symbol
        val parameters = function.valueParameterList?.parameters ?: return emptyList()
        val parametersDependencyGraph = parameters.asSequence().flatMap { parameter ->
            parameter.name?.let { parameterName ->
                parameter.defaultValue
                    ?.childrenDfsSequence()
                    ?.filterIsInstance<KtNameReferenceExpression>()
                    ?.mapNotNull { it.resolveToCall()?.singleVariableAccessCall()?.partiallyAppliedSymbol?.symbol as? KaValueParameterSymbol }
                    ?.filter { it.containingDeclaration == functionSymbol }
                    ?.map { DirectedGraph.Edge(from = it.name.asString(), to = parameterName) }
                    ?.toList()
            } ?: emptyList()
        }.toSet()
        val parametersSortedTopologically = DirectedGraph(parametersDependencyGraph).sortTopologically() ?: return emptyList()
        val parameterToIndex = parameters.asSequence().mapIndexedNotNull { index, parameter ->
            parameter.name?.let { it to index }
        }.toMap()
        val parametersToSortSorted = parametersSortedTopologically.flatMap { it.sortedBy(parameterToIndex::get) }
        val parametersToSortSortedIterator = parametersToSortSorted.iterator()
        val sortedParameters = parameters.mapNotNull {
            val parameterName = it.name
            if (parameterName in parametersToSortSorted) parametersToSortSortedIterator.next()
            else parameterName
        }
        if (sortedParameters.size != parameters.size) return emptyList()
        return listOf(ReorderParametersFix(function, sortedParameters))
    }

    private class ReorderParametersFix(
        element: KtNamedFunction,
        val sortedParameters: List<String>,
    ) : KotlinQuickFixAction<KtNamedFunction>(element) {
        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val function = element ?: return

            val descriptor = KotlinMethodDescriptor(function)

            val changeInfo = KotlinChangeInfo(descriptor)

            if (descriptor.parametersCount == sortedParameters.size) {
                val parameters = changeInfo.newParameters
                val parameterToIndex = sortedParameters.withIndex().associate { it.value to it.index }
                parameters.sortBy { parameterToIndex[it.name] ?: -1 /*-1 is for receiver*/ }
                val receiverParameterInfo = changeInfo.receiverParameterInfo

                changeInfo.clearParameters()

                changeInfo.receiverParameterInfo = receiverParameterInfo
                for (info in parameters) {
                    changeInfo.addParameter(info)
                }
            }

            KotlinChangeSignatureProcessor(file.project, changeInfo).run()
        }

        override fun startInWriteAction(): Boolean = false
        override fun getText(): String = getFamilyName()
        override fun getFamilyName(): String = KotlinBundle.message("reorder.parameters")
    }
}