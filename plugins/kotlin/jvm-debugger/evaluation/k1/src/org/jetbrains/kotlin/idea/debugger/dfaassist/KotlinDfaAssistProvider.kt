// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.dfaassist

import com.intellij.codeInspection.dataFlow.TypeConstraint
import com.intellij.codeInspection.dataFlow.TypeConstraints
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.dfaassist.DebuggerDfaListener
import com.intellij.debugger.engine.dfaassist.DfaAssistProvider
import com.intellij.debugger.jdi.StackFrameProxyEx
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.util.ThreeState
import com.intellij.xdebugger.impl.dfaassist.DfaHint
import com.sun.jdi.Location
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.debugger.base.util.ClassNameCalculator
import org.jetbrains.kotlin.idea.debugger.evaluate.variables.EvaluatorValueConverter
import org.jetbrains.kotlin.idea.inspections.dfa.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.org.objectweb.asm.Type as AsmType

class KotlinDfaAssistProvider : DfaAssistProvider {
    override fun locationMatches(element: PsiElement, location: Location): Boolean {
        val jdiClassName = location.method().declaringType().name()
        val file = element.containingFile
        if (file !is KtFile) return false
        val classNames = ClassNameCalculator.getClassNames(file)
        return element.parentsWithSelf.any { e -> classNames[e] == jdiClassName }
    }

    override fun getAnchor(element: PsiElement): KtExpression? {
        var cur = element
        while (cur is PsiWhiteSpace || cur is PsiComment) {
            cur = cur.nextSibling ?: return null
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
                    val jvmName = KotlinPsiHeuristics.getJvmName(declarationDescriptor.fqNameSafe)
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
                val field = psiVariable.name?.let { DebuggerUtils.findField(jdiQualifier.referenceType(), it) }
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
        return KotlinDebuggerDfaListener()
    }

    override fun constraintFromJvmClassName(anchor: PsiElement, jvmClassName: String): TypeConstraint {
        val classDef = KtClassDef.fromJvmClassName(anchor as KtElement, jvmClassName) ?: return TypeConstraints.TOP
        return if (classDef.cls.kind == ClassKind.OBJECT) TypeConstraints.singleton(classDef) else TypeConstraints.exactClass(classDef)
    }

    class KotlinDebuggerDfaListener : DebuggerDfaListener {
        val hints = hashMapOf<PsiElement, DfaHint>()

        override fun beforePush(args: Array<out DfaValue>, value: DfaValue, anchor: DfaAnchor, state: DfaMemoryState) {
            val dfType = state.getDfType(value)
            var psi = when (anchor) {
                is KotlinAnchor.KotlinExpressionAnchor -> {
                    if (!shouldTrackExpressionValue(anchor.expression)) return
                    if (KotlinConstantConditionsInspection.shouldSuppress(dfType, anchor.expression) && 
                        dfType.tryNegate()?.let { negated -> KotlinConstantConditionsInspection.shouldSuppress(negated, anchor.expression) } != false) {
                        return
                    }
                    anchor.expression
                }

                is KotlinAnchor.KotlinWhenConditionAnchor -> anchor.condition
                else -> return
            }
            var hint = DfaHint.ANY_VALUE
            if (dfType === DfTypes.TRUE) {
                hint = DfaHint.TRUE
            } else if (dfType === DfTypes.FALSE) {
                hint = DfaHint.FALSE
            } else if (dfType === DfTypes.NULL) {
                val parent = psi.parent
                if (parent is KtPostfixExpression && parent.operationToken == KtTokens.EXCLEXCL) {
                    hint = DfaHint.NPE
                } else if (parent is KtBinaryExpressionWithTypeRHS && parent.operationReference.textMatches("as")) {
                    val typeReference = parent.right
                    val type =
                        typeReference?.getAbbreviatedTypeOrType(typeReference.safeAnalyzeNonSourceRootCode(BodyResolveMode.FULL))
                    if (type != null && !type.isMarkedNullable) {
                        hint = DfaHint.NPE
                        psi = parent.operationReference
                    }
                } else if (parent is KtBinaryExpression && parent.operationToken == KtTokens.ELVIS) {
                    hint = DfaHint.NULL
                } else if (psi is KtBinaryExpressionWithTypeRHS && psi.operationReference.textMatches("as?")) {
                    hint = DfaHint.NULL
                }
            }
            hints.merge(psi, hint, DfaHint::merge)
        }

        override fun onCondition(problem: UnsatisfiedConditionProblem, value: DfaValue, failed: ThreeState, state: DfaMemoryState) {
            if (problem is KotlinProblem.KotlinCastProblem) {
                hints.merge(
                    problem.cast.operationReference,
                    if (failed == ThreeState.YES) DfaHint.CCE else DfaHint.NONE,
                    DfaHint::merge
                )
            }
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
            // It's enough to report for parent only
            return (parent as? KtPrefixExpression)?.operationToken != KtTokens.EXCL
        }

        override fun computeHints(): Map<PsiElement, DfaHint> {
            hints.values.removeIf { h -> h.title == null }
            return hints
        }
    }
}