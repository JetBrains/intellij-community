// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiClass
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.jetbrains.eval4j.jdi.asValue
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.diagnostics
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.javaInterop.namedClassSymbol
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.resolution.successful
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.resolution.variable
import org.jetbrains.kotlin.analysis.api.scopes.memberScope
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.namedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
import org.jetbrains.kotlin.analysis.api.types.abbreviationOrSelf
import org.jetbrains.kotlin.analysis.api.types.defaultType
import org.jetbrains.kotlin.analysis.api.types.defaultTypeWithStarProjections
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.analysis.api.types.withNullability
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.base.util.safeThisObject
import org.jetbrains.kotlin.idea.debugger.base.util.safeVisibleVariableByName
import org.jetbrains.kotlin.idea.debugger.core.ClassNameProvider
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CodeFragmentCompilationStats
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtClassOrObject
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolution.KtResolvableCall
import org.jetbrains.kotlin.types.Variance

internal interface KotlinCodeFragmentWrapper {
    fun transformIfNeeded(codeFragment: KtCodeFragment)
}

internal class KotlinValueClassToStringWrapper(val stats: CodeFragmentCompilationStats) : KotlinCodeFragmentWrapper {

    companion object {
        private val LOG = Logger.getInstance(KotlinValueClassToStringWrapper::class.java)
    }

    override fun transformIfNeeded(codeFragment: KtCodeFragment) {
        val (expression, expressionText) = extractExpressionWithText(codeFragment) ?: return

        val needsWrapping = stats.startAndMeasureAnalysisUnderReadAction {
            analyze(expression) {
                val ktUsualClassType = expression.expressionType as? KaUsualClassType
                val ktNamedClassOrObjectSymbol = ktUsualClassType?.symbol as? KaNamedClassSymbol
                ktNamedClassOrObjectSymbol?.isInline == true
            }
        }.getOrNull()

        if (needsWrapping != true) return

        replaceExpression(expression, "($expressionText) as Any?")

        return
    }

    private fun replaceExpression(expression: KtExpression, newExpressionText: String) {
        val newExpression = runReadAction {
            KtPsiFactory(expression.project).createExpression(newExpressionText)
        }

        invokeAndWaitIfNeeded {
            expression.project.executeWriteCommand(
                KotlinDebuggerEvaluationBundle.message("wrap.expression")
            ) {
                expression.replace(newExpression)
            }
        }
    }

    private fun extractExpressionWithText(codeFragment: KtCodeFragment): Pair<KtExpression, String>? =
        runReadAction {
            val expression = when (codeFragment) {
                is KtExpressionCodeFragment -> codeFragment.getContentElement()
                is KtBlockCodeFragment -> codeFragment.getContentElement().statements.lastOrNull()
                else -> {
                    LOG.error("Invalid code fragment type: ${codeFragment.javaClass}")
                    null
                }
            }

            if (expression == null) {
                null
            } else {
                Pair(expression, expression.text)
            }
        }
}

internal class KotlinRuntimeTypeCastWrapper(
    private val stats: CodeFragmentCompilationStats,
    private val executionContext: ExecutionContext,
) : KotlinCodeFragmentWrapper {

    companion object {
        private val LOG = Logger.getInstance(KotlinRuntimeTypeCastWrapper::class.java)
        private const val MAX_ITERATIONS = 20
    }

    private val receiverValueCache = HashMap<String, Value?>()

    override fun transformIfNeeded(codeFragment: KtCodeFragment) {
        var iterations = 0
        while (iterations < MAX_ITERATIONS) {
            iterations++

            val target = stats.startAndMeasureAnalysisUnderReadAction {
                findRewriteTarget(codeFragment)
            }.getOrNull() ?: return

            val runtimeTypeText = computeRuntimeCastType(codeFragment, target) ?: return

            rewrite(target.dotQualified, target.receiverText, target.selectorText, runtimeTypeText)
        }
    }

    private class RewriteTarget(
        val dotQualified: KtDotQualifiedExpression,
        val receiver: KtExpression,
        val receiverText: String,
        val selectorText: String,
    )

    context(_: KaSession)
    private fun findRewriteTargetInSession(codeFragment: KtCodeFragment): RewriteTarget? {
        val content = contentExpression(codeFragment) ?: return null

        for (dotQualified in content.collectDescendantsOfType<KtDotQualifiedExpression>()) {
            val selector = dotQualified.selectorExpression as? KtNameReferenceExpression ?: continue
            val receiver = dotQualified.receiverExpression

            if (!isSimpleSideEffectFreeReceiver(receiver)) continue

            val isUnresolved = selector.diagnostics().directOnly(true).any {
                it.factoryName == "UNRESOLVED_REFERENCE" || it.factoryName == "UNRESOLVED_REFERENCE_WRONG_RECEIVER"
            }
            if (!isUnresolved) continue

            val receiverIsValue = when (receiver) {
                is KtThisExpression -> true
                else -> (receiver as? KtResolvableCall)?.tryResolveCall()?.successful?.variable?.symbol != null
            }
            if (!receiverIsValue) continue

            return RewriteTarget(
                dotQualified = dotQualified,
                receiver = receiver,
                receiverText = receiver.text,
                selectorText = selector.getReferencedName(),
            )
        }
        return null
    }

    private fun findRewriteTarget(codeFragment: KtCodeFragment): RewriteTarget? =
        analyze(codeFragment) {
            findRewriteTargetInSession(codeFragment)
        }

    private fun computeRuntimeCastType(codeFragment: KtCodeFragment, target: RewriteTarget): String? {
        val value = evaluateReceiver(codeFragment, target.receiver, target.receiverText) ?: return null

        val project = executionContext.evaluationContext.project
        val scope = executionContext.evaluationContext.debugProcess.searchScope

        val asmType = value.asValue().asmType
        val className = asmType.internalName.replace('/', '.')

        val memberName = Name.identifier(target.selectorText)

        return runReadAction {
            val psiClass = DebuggerUtils.findClass(className, project, scope, true) as? PsiClass
            val localClass = if (psiClass == null) findLocalClassByName(codeFragment, className) else null
            if (psiClass == null && localClass == null) return@runReadAction null

            analyze(codeFragment) {
                val classSymbol = localClass?.namedClassSymbol
                    ?: psiClass?.asKtClassOrObject()?.namedClassSymbol
                    ?: psiClass?.namedClassSymbol
                    ?: return@analyze null

                if (!memberExistsOn(classSymbol, memberName)) return@analyze null

                val classId = classSymbol.defaultType.symbol?.classId
                val isLocal = classId == null

                if (classSymbol.typeParameters.isNotEmpty() && classId != null) {
                    val recovered = recoverCastTypeFromSource(target.receiver, classId)
                    if (recovered != null) return@analyze recovered
                }

                if (!isLocal) {
                    classSymbol.defaultType.replaceTypeParametersWithStarProjections()?.render(position = Variance.INVARIANT)
                } else {
                    val simpleName = classSymbol.name?.asString() ?: return@analyze null
                    val arity = classSymbol.typeParameters.size
                    if (arity == 0) simpleName
                    else simpleName + List(arity) { "*" }.joinToString(prefix = "<", separator = ", ", postfix = ">")
                }
            }
        }
    }

    private fun findLocalClassByName(codeFragment: KtCodeFragment, className: String): KtClassOrObject? {
        val contextFile = codeFragment.context?.containingFile as? KtFile ?: return null
        val classNameProvider = ClassNameProvider(ClassNameProvider.Configuration.DEFAULT)
        return contextFile.collectDescendantsOfType<KtClassOrObject> { it.name != null }
            .firstOrNull { className in classNameProvider.getCandidatesForElement(it) }
    }

    context(_: KaSession)
    private fun recoverCastTypeFromSource(receiver: KtExpression, runtimeClassId: ClassId): String? {
        fun candidate(type: KaType?): KaClassType? =
            (type as? KaClassType)
                ?.takeIf { it.classId == runtimeClassId && it.typeArguments.isNotEmpty() && !it.hasErrorType() }

        val useSite = candidate(receiver.expressionType)
        val fromInitializer = if (useSite != null) null else candidate(initializerTypeOfValReceiver(receiver))
        val chosen = useSite ?: fromInitializer ?: return null

        return chosen.withNullability(false).render(position = Variance.INVARIANT)
    }

    context(_: KaSession)
    private fun initializerTypeOfValReceiver(receiver: KtExpression): KaType? {
        if (receiver !is KtNameReferenceExpression) return null
        val variable = receiver.tryResolveCall()?.successful?.variable?.symbol ?: return null
        if (!variable.isVal) return null
        val property = variable.psi as? KtProperty ?: return null
        return property.initializer?.expressionType
    }

    private fun KaClassType.hasErrorType(): Boolean {
        if (this is KaErrorType) return true
        return typeArguments.any { (it.type as? KaClassType)?.hasErrorType() == true || it.type is KaErrorType }
    }

    private fun evaluateReceiver(codeFragment: KtCodeFragment, receiver: KtExpression, receiverText: String): Value? {
        if (receiverValueCache.containsKey(receiverText)) return receiverValueCache[receiverText]

        val plainName = runReadAction {
            when (receiver) {
                is KtThisExpression -> if (receiver.getLabelName() == null) "" else null // "" marks unlabeled `this`
                is KtNameReferenceExpression -> receiver.getReferencedName()
                else -> null // qualified chain etc.: fall back to compilation
            }
        }
        val direct: Value? = try {
            runReadAction {
                when {
                    plainName == null -> null
                    receiver is KtThisExpression ->
                        executionContext.evaluationContext.computeThisObject() as? ObjectReference
                            ?: executionContext.frameProxy.safeThisObject()
                    else ->
                        executionContext.frameProxy.safeVisibleVariableByName(plainName)
                            ?.let { executionContext.frameProxy.getValue(it) }
                }
            }
        } catch (e: EvaluateException) {
            null
        }
        if (direct != null) {
            receiverValueCache[receiverText] = direct
            return direct
        }

        val value = try {
            val evaluationContext = executionContext.evaluationContext
            val receiverFragment = runReadAction {
                KtPsiFactory(codeFragment.project).createBlockCodeFragment(receiverText, codeFragment.context)
            }
            val evaluator = runReadAction {
                KotlinEvaluatorBuilder.build(receiverFragment, ContextUtil.getSourcePosition(evaluationContext))
            }
            evaluator.evaluate(evaluationContext)
        } catch (e: EvaluateException) {
            LOG.debug("Failed to evaluate receiver for runtime-type cast: $receiverText", e)
            null
        }

        receiverValueCache[receiverText] = value
        return value
    }

    private fun rewrite(
        dotQualified: KtDotQualifiedExpression,
        receiverText: String,
        selectorText: String,
        runtimeTypeText: String,
    ) {
        val newExpressionText = "($receiverText as $runtimeTypeText).$selectorText"

        val newExpression = runReadAction {
            KtPsiFactory(dotQualified.project).createExpression(newExpressionText)
        }

        invokeAndWaitIfNeeded {
            dotQualified.project.executeWriteCommand(
                KotlinDebuggerEvaluationBundle.message("wrap.expression")
            ) {
                dotQualified.replace(newExpression)
            }
        }
    }

    private fun contentExpression(codeFragment: KtCodeFragment): KtExpression? =
        when (codeFragment) {
            is KtExpressionCodeFragment -> codeFragment.getContentElement()
            is KtBlockCodeFragment -> codeFragment.getContentElement().statements.lastOrNull()
            else -> {
                LOG.error("Invalid code fragment type: ${codeFragment.javaClass}")
                null
            }
        }

    private fun isSimpleSideEffectFreeReceiver(receiver: KtExpression): Boolean =
        when (receiver) {
            is KtNameReferenceExpression -> true
            is KtThisExpression -> true
            is KtDotQualifiedExpression -> {
                val selectorSimple = receiver.selectorExpression is KtNameReferenceExpression
                selectorSimple && isSimpleSideEffectFreeReceiver(receiver.receiverExpression)
            }
            else -> false
        } && receiver.collectDescendantsOfType<KtCallExpression>().isEmpty()

    context(_: KaSession)
    private fun memberExistsOn(classSymbol: KaClassSymbol, name: Name): Boolean =
        classSymbol.memberScope.callables(name).any()

    context(_: KaSession)
    private fun KaType.replaceTypeParametersWithStarProjections(): KaType? =
        (abbreviationOrSelf.symbol as? KaClassLikeSymbol)?.defaultTypeWithStarProjections
}

