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
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.debugger.ClassNameCalculator
import org.jetbrains.kotlin.idea.debugger.evaluate.variables.EvaluatorValueConverter
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor
import org.jetbrains.kotlin.idea.inspections.dfa.KtThisDescriptor
import org.jetbrains.kotlin.idea.inspections.dfa.KtVariableDescriptor
import org.jetbrains.kotlin.idea.util.toJvmFqName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.org.objectweb.asm.Type as AsmType

class KotlinDfaAssistProvider : DfaAssistProvider {
    override fun locationMatches(element: PsiElement, location: Location): Boolean {
        val jdiClassName = location.method().declaringType().name()
        val file = element.containingFile
        if (file !is KtFile) return false
        val classNames = ClassNameCalculator.getClassNames(file)
        val psiClassName = element.parentsWithSelf.firstNotNullOfOrNull { e -> classNames[e] }
        return psiClassName == jdiClassName
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
        val qualifier = dfaVar.qualifier
        val psiVariable = dfaVar.psiVariable
        if (qualifier == null) {
            val descriptor = dfaVar.descriptor
            if (descriptor is KtThisDescriptor) {
                val declarationDescriptor = descriptor.descriptor
                if (declarationDescriptor is FunctionDescriptor) {
                    val thisName = "\$this\$${declarationDescriptor.name}"
                    val thisVar = proxy.visibleVariableByName(thisName)
                    if (thisVar != null) {
                        return postprocess(proxy.getVariableValue(thisVar))
                    }
                    return null
                }
                val thisObject = proxy.thisObject()
                if (thisObject != null) {
                    val signature = AsmType.getType(thisObject.referenceType().signature()).className
                    val jvmName = declarationDescriptor.fqNameSafe.toJvmFqName
                    if (signature == jvmName) {
                        return thisObject
                    }
                }
                // TODO: support `this` references for outer types, etc.
                return null
            }
            else if (descriptor is KtVariableDescriptor && psiVariable is KtCallableDeclaration) {
                // TODO: check/support inlined functions
                val variable = proxy.visibleVariableByName((psiVariable as KtNamedDeclaration).name)
                if (variable != null) {
                    return postprocess(proxy.getVariableValue(variable))
                }
            }
        } else {
            val jdiQualifier = getJdiValueForDfaVariable(proxy, qualifier, anchor)
            if (jdiQualifier is ObjectReference && psiVariable is KtCallableDeclaration) {
                val type = jdiQualifier.referenceType()
                val field = type.fieldByName(psiVariable.name)
                if (field != null) {
                    return postprocess(jdiQualifier.getValue(field))
                }
            }
        }
        return null
    }

    private fun postprocess(value: Value?): Value {
        return DfaAssistProvider.wrap(EvaluatorValueConverter.unref(value))
    }

    override fun createListener(): DebuggerDfaListener {
        return object : DebuggerDfaListener {
            val hints = hashMapOf<PsiElement, DfaHint>()

            override fun beforePush(args: Array<out DfaValue>, value: DfaValue, anchor: DfaAnchor, state: DfaMemoryState) {
                val psi = when (anchor) {
                    is KotlinAnchor.KotlinExpressionAnchor -> {
                        if (shouldTrackExpressionValue(anchor.expression)) anchor.expression
                        else return
                    }
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

            private fun shouldTrackExpressionValue(expr: KtExpression): Boolean {
                if (expr is KtBinaryExpression) {
                    val token = expr.operationToken
                    // Report right hand of assignment only
                    if (token == KtTokens.EQ) return false
                    // For boolean expression, report individual operands only, to avoid clutter
                    if (token == KtTokens.ANDAND || token == KtTokens.OROR) return false
                }
                var parent = expr.parent
                while (parent is KtParenthesizedExpression) {
                    parent = parent.parent
                }
                if ((parent as? KtPrefixExpression)?.operationToken == KtTokens.EXCL) {
                    // It's enough to report for parent only
                    return false
                }
                return true
            }

            override fun computeHints(): Map<PsiElement, DfaHint> {
                hints.values.removeIf { h -> h.title == null }
                return hints
            }
       }
    }
}