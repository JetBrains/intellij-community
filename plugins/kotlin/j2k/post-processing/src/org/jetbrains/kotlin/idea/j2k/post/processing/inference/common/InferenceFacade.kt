// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.idea.j2k.post.processing.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory

class InferenceFacade(
    private val typeVariablesCollector: ContextCollector,
    private val constraintsCollectorAggregator: ConstraintsCollectorAggregator,
    private val boundTypeCalculator: BoundTypeCalculator,
    private val stateUpdater: StateUpdater,
    private val defaultStateProvider: DefaultStateProvider,
    private val renderDebugTypes: Boolean = false,
    private val printDebugConstraints: Boolean = false
) {
    fun runOn(elements: List<KtElement>) {
        val inferenceContext = runReadAction { typeVariablesCollector.collectTypeVariables(elements) }
        val constraints = runReadAction {
            constraintsCollectorAggregator.collectConstraints(boundTypeCalculator, inferenceContext, elements)
        }

        val initialConstraints = if (renderDebugTypes) constraints.map { it.copy() } else null
        runReadAction {
            Solver(inferenceContext, printDebugConstraints, defaultStateProvider).solveConstraints(constraints)
        }

        if (renderDebugTypes) {
            with(DebugPrinter(inferenceContext)) {
                runUndoTransparentActionInEdt(inWriteAction = true) {
                    for ((expression, boundType) in boundTypeCalculator.expressionsWithBoundType()) {
                        val comment = KtPsiFactory(expression.project).createComment("/*${boundType.asString()}*/")
                        expression.parent.addAfter(comment, expression)
                    }
                    for (element in elements) {
                        element.addTypeVariablesNames()
                    }
                    for (element in elements) {
                        val psiFactory = KtPsiFactory(element.project)
                        element.add(psiFactory.createNewLine(lineBreaks = 2))
                        for (constraint in initialConstraints!!) {
                            element.add(psiFactory.createComment("//${constraint.asString()}"))
                            element.add(psiFactory.createNewLine(lineBreaks = 1))
                        }
                    }
                }
            }
        }
        runUndoTransparentActionInEdt(inWriteAction = true) {
            stateUpdater.updateStates(inferenceContext)
        }
    }
}