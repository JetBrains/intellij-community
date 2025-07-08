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
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.dfaassist.DebuggerDfaListener
import com.intellij.debugger.engine.dfaassist.DfaAssistProvider
import com.intellij.debugger.jdi.StackFrameProxyEx
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind
import com.intellij.openapi.application.readAction
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
import org.jetbrains.kotlin.idea.debugger.base.util.ClassNameCalculator
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinDebuggerConstants
import org.jetbrains.kotlin.idea.debugger.base.util.getInlineDepth
import org.jetbrains.kotlin.idea.debugger.evaluate.variables.EvaluatorValueConverter
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinAnchor
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinProblem
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import java.util.*
import org.jetbrains.org.objectweb.asm.Type as AsmType

private class K2DfaAssistProvider : DfaAssistProvider {
    override suspend fun locationMatches(element: PsiElement, location: Location): Boolean {
        val jdiClassName = location.method().declaringType().name()
        return readAction {
            val file = element.containingFile
            if (file !is KtFile) return@readAction false
            val classNames = ClassNameCalculator.getClassNames(file)
            element.parentsWithSelf.any { e -> classNames[e] == jdiClassName }
        }
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

    override suspend fun getJdiValueForDfaVariable(proxy: StackFrameProxyEx, descriptor: VariableDescriptor, anchor: PsiElement): Value? {
        if (anchor !is KtElement) return null
        val value = getJdiValueForDfaVariableInner(proxy, descriptor, anchor)
        if (value != null) {
            if (readAction { (descriptor as? KtBaseDescriptor)?.isInlineClassReference() == true }) {
                return DfaAssistProvider.InlinedValue(value)
            }
        }
        return value
    }

    private suspend fun getJdiValueForDfaVariableInner(
        proxy: StackFrameProxyEx,
        descriptor: VariableDescriptor,
        anchor: KtElement
    ): Value? {
        val variables = (proxy as StackFrameProxyImpl).visibleVariables()
        val inlineDepth = getInlineDepth(variables)
        val inlineSuffix = KotlinDebuggerConstants.INLINE_FUN_VAR_SUFFIX.repeat(inlineDepth)
        when (descriptor) {
            is KtLambdaThisVariableDescriptor -> {
                val regex = readAction {
                    val scopeName = (descriptor.lambda.parentOfType<KtFunction>() as? KtNamedFunction)?.name
                    val scopePart = scopeName?.replace(Regex("[^\\p{L}\\d]"), "_")?.let(Regex::escape) ?: ".+"
                    val inlinedPart = Regex.escape(inlineSuffix)
                    Regex("\\\$this\\$${scopePart}(_\\w+)?_u\\d+lambda_u\\d+$inlinedPart")
                }
                val lambdaThis = proxy.stackFrame.visibleVariables().filter { it.name().matches(regex) }
                if (lambdaThis.size == 1) {
                    return postprocess(proxy.stackFrame.getValue(lambdaThis.first()))
                }
            }

            is KtThisDescriptor -> {
                val pointer = descriptor.classDef?.pointer
                val contextName = descriptor.contextName
                if (contextName != null) {
                    val thisName = "\$this$${contextName}$inlineSuffix"
                    val thisVar = proxy.visibleVariableByName(thisName)
                    if (thisVar != null) {
                        return postprocess(proxy.getVariableValue(thisVar))
                    }
                }
                val nameString = readAction {
                    analyze(anchor) { (pointer?.restoreSymbol() as? KaNamedClassSymbol)?.classId?.asSingleFqName() }
                }
                if (nameString != null) {
                    if (inlineDepth > 0) {
                        val thisName = AsmUtil.INLINE_DECLARATION_SITE_THIS + inlineSuffix
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

            is KtVariableDescriptor -> {
                val pointer = descriptor.pointer
                val result = readAction {
                    analyze(anchor) {
                        val symbol = pointer.restoreSymbol()
                        if (symbol is KaJavaFieldSymbol && symbol.isStatic) {
                            val classId = (symbol.containingDeclaration as? KaNamedClassSymbol)?.classId
                            if (classId != null) {
                                val className = JvmClassName.byClassId(classId).internalName.replace("/", ".")
                                val fieldName = symbol.name.identifier
                                return@readAction VariableResult.JavaField(className, fieldName)
                            }
                            return@readAction null
                        }
                        if (symbol is KaVariableSymbol) {
                            val name = symbol.name.asString() + inlineSuffix
                            val expectedType = symbol.returnType
                            val isNonNullPrimitiveType = expectedType.isPrimitive && !expectedType.canBeNull
                            return@readAction VariableResult.Variable(name, symbol.psi, isNonNullPrimitiveType)
                        }
                    }
                    null
                }
                when (result) {
                    is VariableResult.JavaField -> {
                        val declaringClasses = proxy.virtualMachine.classesByName(result.className)
                        if (declaringClasses.size == 1) {
                            val declaringClass = declaringClasses.first()
                            val field = DebuggerUtils.findField(declaringClass, result.fieldName)
                            if (field != null && field.isStatic) {
                                return postprocess(declaringClass.getValue(field))
                            }
                        }
                    }

                    is VariableResult.Variable -> {
                        var variable = proxy.visibleVariableByName(result.name)
                        var value: Value? = null
                        if (variable == null) {
                            val isValidScope = readAction {
                                val psi = result.psi ?: return@readAction false
                                val scope = anchor.getScope()
                                scope != null && psi.containingFile == scope.containingFile
                                        && !scope.isAncestor(psi)
                            }
                            if (isValidScope) {
                                // Captured variable
                                val capturedName = AsmUtil.CAPTURED_PREFIX + result.name
                                variable = proxy.visibleVariableByName(capturedName)
                                if (variable == null) {
                                    // Captured variable in Kotlin 1.x
                                    val thisObject = proxy.thisObject()
                                    val thisType = thisObject?.referenceType()
                                    if (thisType != null) {
                                        val capturedField = DebuggerUtils.findField(thisType, capturedName)
                                        if (capturedField != null) {
                                            value = postprocess(thisObject.getValue(capturedField))
                                        }
                                    }
                                } else {
                                    value = postprocess(proxy.getVariableValue(variable))
                                }
                            }
                        } else {
                            value = postprocess(proxy.getVariableValue(variable))
                        }
                        if (value != null) {
                            if (inlineDepth > 0 && value.type() is PrimitiveType && !result.isNonNullPrimitiveReturnType) {
                                val typeKind = JvmPrimitiveTypeKind.getKindByName(value.type().name())
                                if (typeKind != null) {
                                    val referenceType = proxy.virtualMachine.classesByName(typeKind.boxedFqn).firstOrNull()
                                    if (referenceType != null) {
                                        value = DfaAssistProvider.BoxedValue(value, referenceType)
                                    }
                                }
                            }
                            return value
                        }
                    }

                    null -> {}
                }
            }
        }
        return null
    }

    private fun KtElement.getScope(): KtFunction? {
        var current = this
        while (true) {
            val function = current.parentOfType<KtFunction>()
            if (function != null) {
                val realFun = function.parent as? KtLambdaExpression ?: function
                val arg = realFun.parent as? KtValueArgument
                val call = when (arg) {
                    is KtLambdaArgument -> arg.parent
                    else -> arg?.parent?.parent
                } as? KtCallExpression
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

    override suspend fun getJdiValuesForQualifier(
        proxy: StackFrameProxyEx,
        qualifier: Value,
        descriptors: List<VariableDescriptor>,
        anchor: PsiElement
    ): Map<VariableDescriptor, Value> {
        if (anchor !is KtElement) return emptyMap()
        // Avoid relying on hashCode/equals, as descriptors are known to be deduplicated here
        val map = IdentityHashMap<VariableDescriptor, Value>()
        for (descriptor in descriptors) {
            if (descriptor is KtVariableDescriptor) {
                val pointer = descriptor.pointer
                val result = readAction {
                    analyze(anchor) {
                        val symbol = pointer.restoreSymbol()
                        if (symbol is KaPropertySymbol) {
                            val parent = symbol.containingDeclaration
                            if (parent is KaNamedClassSymbol && parent.isInline) {
                                // Inline class sole property is represented by inline class itself
                                return@readAction QualifierVariableResult.InlineClassProperty
                            }
                        }
                        if (symbol is KaVariableSymbol) {
                            return@readAction QualifierVariableResult.NamedVariable(symbol.name.asString())
                        }
                        null
                    }
                }
                when (result) {
                    QualifierVariableResult.InlineClassProperty -> {
                        map[descriptor] = if (qualifier is DfaAssistProvider.InlinedValue) qualifier.value else qualifier
                    }

                    is QualifierVariableResult.NamedVariable -> {
                        val type = (qualifier as? ObjectReference)?.referenceType()
                        if (type != null) {
                            val field = DebuggerUtils.findField(type, result.name)
                            if (field != null) {
                                map[descriptor] = postprocess(qualifier.getValue(field))
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
        return map
    }

    private sealed interface VariableResult {
        data class JavaField(val className: String, val fieldName: String) : VariableResult
        data class Variable(val name: String, val psi: PsiElement?, val isNonNullPrimitiveReturnType: Boolean) : VariableResult
    }

    private sealed interface QualifierVariableResult {
        object InlineClassProperty : QualifierVariableResult
        data class NamedVariable(val name: String) : QualifierVariableResult
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
