// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.dfaassist

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.debugger.engine.dfaassist.DebuggerDfaListener
import com.intellij.debugger.engine.dfaassist.DfaAssistProvider
import com.intellij.debugger.engine.dfaassist.DfaHint
import com.intellij.debugger.jdi.StackFrameProxyEx
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.sun.jdi.Location
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor
import org.jetbrains.kotlin.psi.*

class KotlinDfaAssistProvider : DfaAssistProvider {
    override fun locationMatches(element: PsiElement, location: Location): Boolean {
        // TODO: more precise check
        return true
    }
    override fun getAnchor(element: PsiElement): KtExpression? {
        var cur = element
        while (cur is PsiWhiteSpace || cur is PsiComment) {
            cur = element.nextSibling
        }
        while (true) {
            val parent = cur.parent
            if (parent is KtBlockExpression || parent is KtFunction) {
                return cur as? KtExpression
            }
            if (parent == null || cur.startOffsetInParent > 0) return null
            cur = parent
        }
    }

    override fun getCodeBlock(anchor: PsiElement): KtExpression? {
        if (anchor !is KtExpression) return null
        when (val parent = anchor.parent) {
            is KtBlockExpression -> return parent
            is KtFunction -> return anchor
            else -> return null
        }
    }

    override fun getJdiValueForDfaVariable(proxy: StackFrameProxyEx, dfaVar: DfaVariableValue, anchor: PsiElement): Value? {
        if (dfaVar.qualifier == null) {
            val psiVariable = dfaVar.psiVariable
            if (psiVariable is KtParameter || psiVariable is KtProperty && psiVariable.isLocal) {
                // TODO: more robust mapping
                // TODO: take into account non-local parameters (e.g., data class parameters)
                // TODO: check/support inlined functions
                val variable = proxy.visibleVariableByName((psiVariable as KtNamedDeclaration).name)
                if (variable != null) {
                    return postprocess(proxy.getVariableValue(variable))
                }
            }
        }
        // TODO: support qualified vars
        return null
    }

    private fun postprocess(value: Value?): Value {
        if (value is ObjectReference) {
            val type = value.referenceType()
            if (type.name().startsWith("kotlin.jvm.internal.Ref$")) {
                val field = type.fieldByName("element")
                if (field != null) {
                    return DfaAssistProvider.wrap(value.getValue(field))
                }
            }
        }
        return DfaAssistProvider.wrap(value)
    }

    override fun createListener(): DebuggerDfaListener {
        return object : DebuggerDfaListener {
            val hints = hashMapOf<PsiElement, DfaHint>()

            override fun beforePush(args: Array<out DfaValue>, value: DfaValue, anchor: DfaAnchor, state: DfaMemoryState) {
                val psi = when (anchor) {
                    is KotlinAnchor.KotlinExpressionAnchor -> anchor.expression
                    is KotlinAnchor.KotlinWhenConditionAnchor -> anchor.condition
                    else -> return
                }
                var hint = DfaHint.ANY_VALUE
                val dfType = state.getDfType(value)
                if (dfType === DfTypes.TRUE) {
                    hint = DfaHint.TRUE
                } else if (dfType === DfTypes.FALSE) {
                    hint = DfaHint.FALSE
                }
                hints.merge(psi, hint, DfaHint::merge)
            }

            override fun computeHints(): Map<PsiElement, DfaHint> {
                hints.values.removeIf { h -> h.title == null }
                return hints
            }
       }
    }
}