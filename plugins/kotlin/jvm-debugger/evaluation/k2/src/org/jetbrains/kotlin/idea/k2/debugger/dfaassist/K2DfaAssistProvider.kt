// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.dfaassist

import com.intellij.codeInspection.dataFlow.NullabilityProblemKind.NullabilityProblem
import com.intellij.codeInspection.dataFlow.TypeConstraint
import com.intellij.codeInspection.dataFlow.TypeConstraints
import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfReferenceType
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.dfaassist.DebuggerDfaListener
import com.intellij.debugger.engine.dfaassist.DfaAssistProvider
import com.intellij.debugger.jdi.StackFrameProxyEx
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.isAncestor
import com.intellij.psi.util.parentOfType
import com.intellij.util.ThreeState
import com.intellij.xdebugger.impl.dfaassist.DfaHint
import com.sun.jdi.Location
import com.sun.jdi.ObjectReference
import com.sun.jdi.PrimitiveType
import com.sun.jdi.Value
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.base.psi.hasInlineModifier
import org.jetbrains.kotlin.idea.debugger.base.util.ClassNameCalculator
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinDebuggerConstants
import org.jetbrains.kotlin.idea.debugger.evaluate.variables.EvaluatorValueConverter
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinProblem
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.org.objectweb.asm.Type as AsmType

class K2DfaAssistProvider : DfaAssistProvider {
    override fun locationMatches(element: PsiElement, location: Location): Boolean {
        val jdiClassName = location.method().declaringType().name()
        val file = element.containingFile
        if (file !is KtFile) return false
        val classNames = ClassNameCalculator.getClassNames(file)
        return element.parentsWithSelf.any { e -> classNames[e] == jdiClassName }
    }

    override fun getAnchor(element: PsiElement): PsiElement? {
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

    override fun getCodeBlock(anchor: PsiElement): PsiElement? {
        if (anchor !is KtExpression) return null
        when (val parent = anchor.parent) {
            is KtBlockExpression -> return parent
            is KtFunction -> return anchor
            else -> return null
        }
    }

    override fun getJdiValueForDfaVariable(
        proxy: StackFrameProxyEx,
        dfaVar: DfaVariableValue,
        anchor: PsiElement
    ): Value? {
        if (anchor !is KtElement) return null
        if ((dfaVar.descriptor as? KtBaseDescriptor)?.isInlineClassReference() == true) return null
        return getJdiValueInner(proxy, dfaVar, anchor)
    }
    
    private fun KtElement.getScope(): KtFunction? {
        var current = this
        while(true) {
            val function = current.parentOfType<KtFunction>()
            if (function != null) {
                val lambda = function.parent as? KtLambdaExpression
                val arg = lambda?.parent as? KtLambdaArgument
                val call = arg?.parent as? KtCallExpression
                if (call != null) {
                    val inline = analyze(call) {
                        val functionCall = call.resolveToCall()?.singleFunctionCallOrNull()
                        (functionCall?.partiallyAppliedSymbol?.symbol as? KaNamedFunctionSymbol)?.isInline == true
                    }
                    if (inline) {
                        current = call
                        continue
                    }
                }
            }
            return function
        }
    }

    private fun getJdiValueInner(
        proxy: StackFrameProxyEx,
        dfaVar: DfaVariableValue,
        anchor: KtElement
    ): Value? {
        val qualifier = dfaVar.qualifier
        val descriptor = dfaVar.descriptor
        val scope = anchor.getScope()
        val inlined = (scope as? KtNamedFunction)?.hasInlineModifier() ?: false
        if (qualifier == null) {
            if (descriptor is KtLambdaThisVariableDescriptor) {
                val scopeName = (descriptor.lambda.parentOfType<KtFunction>() as? KtNamedFunction)?.name
                val scopePart = scopeName?.let(Regex::escape) ?: ".+"
                val inlinedPart = if (inlined) Regex.escape(KotlinDebuggerConstants.INLINE_FUN_VAR_SUFFIX) else ""
                val regex = Regex("\\\$this\\\$${scopePart}_u\\d+lambda_u\\d+$inlinedPart")
                val lambdaThis = proxy.stackFrame.visibleVariables().filter { it.name().matches(regex) }
                if (lambdaThis.size == 1) {
                    return postprocess(proxy.stackFrame.getValue(lambdaThis.first()))
                }
            }
            if (descriptor is KtThisDescriptor) {
                val pointer = descriptor.classDef?.pointer
                val contextName = descriptor.contextName
                if (contextName != null) {
                    var thisName = "\$this\$${contextName}"
                    if (inlined) {
                        thisName += KotlinDebuggerConstants.INLINE_FUN_VAR_SUFFIX
                    }
                    val thisVar = proxy.visibleVariableByName(thisName)
                    if (thisVar != null) {
                        return postprocess(proxy.getVariableValue(thisVar))
                    }
                }
                val nameString = analyze(anchor) { (pointer?.restoreSymbol() as? KaNamedClassSymbol)?.classId?.asSingleFqName() }
                if (nameString != null) {
                    if (inlined) {
                        val thisName =
                            AsmUtil.INLINE_DECLARATION_SITE_THIS + KotlinDebuggerConstants.INLINE_FUN_VAR_SUFFIX
                        val thisVar = proxy.visibleVariableByName(thisName)
                        if (thisVar != null) {
                            return postprocess(proxy.getVariableValue(thisVar))
                        }
                    } else {
                        var thisObject = proxy.thisObject()
                        while (thisObject != null) {
                            val thisType = thisObject.referenceType()
                            val signature = AsmType.getType(thisType.signature()).className
                            val jvmName = KotlinPsiHeuristics.getJvmName(nameString)
                            if (signature == jvmName) return thisObject
                            thisObject = when (val outerClassField = DebuggerUtils.findField(thisType, "this$0")) {
                              null -> null
                              else -> thisObject.getValue(outerClassField) as? ObjectReference
                            }
                        }
                    }
                    if (descriptor.isInlineClassReference()) {
                        // See org.jetbrains.kotlin.backend.jvm.MemoizedInlineClassReplacements.createStaticReplacement
                        val thisVar = proxy.visibleVariableByName("arg0")
                        if (thisVar != null) {
                            return postprocess(proxy.getVariableValue(thisVar))
                        }
                    }
                }
            }
            if (descriptor is KtVariableDescriptor) {
                val pointer = descriptor.pointer
                analyze(anchor) {
                    val symbol = pointer.restoreSymbol()
                    if (symbol is KaJavaFieldSymbol && symbol.isStatic) {
                        val classId = (symbol.containingDeclaration as? KaNamedClassSymbol)?.classId
                        if (classId != null) {
                            val declaringClasses = proxy.virtualMachine.classesByName(JvmClassName.byClassId(classId).internalName.replace("/", "."))
                            if (declaringClasses.size == 1) {
                                val declaringClass = declaringClasses.first()
                                val field = DebuggerUtils.findField(declaringClass, symbol.name.identifier)
                                if (field != null && field.isStatic) {
                                    return postprocess(declaringClass.getValue(field))
                                }
                            }
                        }
                        return null
                    }
                    if (symbol is KaVariableSymbol) {
                        var name = symbol.name.asString()
                        if (inlined) {
                            name += KotlinDebuggerConstants.INLINE_FUN_VAR_SUFFIX
                        }
                        val variable = proxy.visibleVariableByName(name)
                        if (variable != null) {
                            val value = postprocess(proxy.getVariableValue(variable))
                            val expectedType = symbol.returnType
                            if (inlined && value.type() is PrimitiveType && !(expectedType.isPrimitive && !expectedType.canBeNull)) {
                                val typeKind = JvmPrimitiveTypeKind.getKindByName(value.type().name())
                                if (typeKind != null) {
                                    val referenceType = proxy.virtualMachine.classesByName(typeKind.boxedFqn).firstOrNull()
                                    if (referenceType != null) {
                                        return DfaAssistProvider.BoxedValue(value, referenceType)
                                    }
                                }
                            }
                            return value
                        }
                        val psi = symbol.psi
                        if (psi != null && scope != null && psi.containingFile == scope.containingFile && !scope.isAncestor(psi)) {
                            // Maybe captured variable
                            val thisObject = proxy.thisObject()
                            val thisType = thisObject?.referenceType()
                            if (thisType != null) {
                                val capturedField = thisType.fieldByName(AsmUtil.CAPTURED_PREFIX + name)
                                if (capturedField != null) {
                                    return postprocess(thisObject.getValue(capturedField))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val jdiQualifier = getJdiValueInner(proxy, qualifier, anchor)
            if (descriptor is KtVariableDescriptor) {
                val type = (jdiQualifier as? ObjectReference)?.referenceType()
                val pointer = descriptor.pointer
                analyze(anchor) {
                    val symbol = pointer.restoreSymbol()
                    if (symbol is KaPropertySymbol) {
                        val parent = symbol.containingDeclaration
                        if (parent is KaNamedClassSymbol && parent.isInline) {
                            // Inline class sole property is represented by inline class itself
                            return jdiQualifier
                        }
                    }
                    if (symbol is KaVariableSymbol && type != null) {
                        val field = DebuggerUtils.findField(type, symbol.name.asString())
                        if (field != null) {
                            return postprocess(jdiQualifier.getValue(field))
                        }
                    }
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

    override fun constraintFromJvmClassName(anchor: PsiElement, jvmClassName: String): TypeConstraint =
        KtClassDef.fromJvmClassName(anchor as KtElement, jvmClassName)?.asConstraint() ?: TypeConstraints.TOP

    class KotlinDebuggerDfaListener : DebuggerDfaListener {
        val hints = hashMapOf<PsiElement, DfaHint>()

        override fun beforePush(args: Array<out DfaValue>, value: DfaValue, anchor: DfaAnchor, state: DfaMemoryState) {
            var dfType = state.getDfType(value)
            if (dfType is DfReferenceType && dfType.constraint.unboxedType == DfTypes.BOOLEAN) {
                dfType = SpecialField.UNBOX.getFromQualifier(state.getDfTypeIncludingDerived(value))
            }
            var psi = when (anchor) {
                is KotlinAnchor.KotlinExpressionAnchor -> {
                    if (!shouldTrackExpressionValue(anchor.expression)) return
                    if (KotlinConstantConditionsInspection.shouldSuppress(dfType, anchor.expression)) return
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
                    val nullability = analyze(parent) { typeReference?.type?.nullability }
                    if (nullability == KaTypeNullability.NON_NULLABLE) {
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
            if (problem is NullabilityProblem<*>) {
                hints.merge(
                    problem.anchor,
                    if (failed == ThreeState.YES) DfaHint.NPE else DfaHint.NONE,
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
