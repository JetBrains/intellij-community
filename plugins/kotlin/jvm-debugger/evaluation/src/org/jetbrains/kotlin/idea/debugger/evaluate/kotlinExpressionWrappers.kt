// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import java.util.concurrent.Callable
import org.jetbrains.eval4j.jdi.asValue
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.diagnostics
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.resolution.successful
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.resolution.variable
import org.jetbrains.kotlin.analysis.api.scopes.memberScope
import org.jetbrains.kotlin.analysis.api.scopes.staticMemberScope
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.findClass
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
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.base.util.safeThisObject
import org.jetbrains.kotlin.idea.debugger.base.util.safeVisibleVariableByName
import org.jetbrains.kotlin.idea.debugger.core.ClassNameProvider
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CodeFragmentCompilationStats
import org.jetbrains.kotlin.idea.util.application.attachmentByPsiFile
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.idea.util.application.merge
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

/**
 * Shared infrastructure for wrappers that resolve an unresolved `receiver.member` access against the
 * runtime type of a side-effect-free receiver value, and rewrite it in place.
 *
 * Concrete wrappers only decide how to rewrite the access once the runtime class is known
 * (see [buildRewriteText]); candidate discovery, receiver evaluation, runtime-class resolution and
 * the write command are shared here.
 */
internal abstract class KotlinRuntimeTypeMemberWrapper(
    private val stats: CodeFragmentCompilationStats,
    private val executionContext: ExecutionContext,
) : KotlinCodeFragmentWrapper {

    companion object {
        private val LOG = Logger.getInstance(KotlinRuntimeTypeMemberWrapper::class.java)
        private const val MAX_REWRITE_ITERATIONS = 20
    }

    private val receiverValueCache = HashMap<String, Value?>()

    context(_: KaSession)
    protected abstract fun buildRewriteText(
        classSymbol: KaNamedClassSymbol,
        runtimeClassId: ClassId,
        candidate: RewriteCandidate,
    ): String?

    final override fun transformIfNeeded(codeFragment: KtCodeFragment) {
        var iterations = 0
        while (iterations < MAX_REWRITE_ITERATIONS) {
            iterations++

            val candidate = stats.startAndMeasureAnalysisUnderReadAction {
                findCandidate(codeFragment)
            }.getOrNull() ?: return

            val newExpressionText = computeRewriteText(codeFragment, candidate) ?: return

            rewrite(candidate.dotQualified, newExpressionText)
        }

        ReadAction.nonBlocking(Callable {
            reportUnexpectedState(
                codeFragment,
                "Runtime-type member rewriting did not converge after $MAX_REWRITE_ITERATIONS iterations",
            )
        }).executeSynchronously()
    }

    protected class RewriteCandidate(
        val codeFragment: KtCodeFragment,
        val dotQualified: SmartPsiElementPointer<KtDotQualifiedExpression>,
        val receiver: SmartPsiElementPointer<KtExpression>,
        val receiverText: String,
        val calleeName: Name,
        val selectorText: String,
    )

    private fun findCandidate(codeFragment: KtCodeFragment): RewriteCandidate? =
        analyze(codeFragment) {
            findCandidateInSession(codeFragment)
        }

    context(_: KaSession)
    private fun findCandidateInSession(codeFragment: KtCodeFragment): RewriteCandidate? {
        val content = contentExpression(codeFragment) ?: return null

        for (dotQualified in content.collectDescendantsOfType<KtDotQualifiedExpression>()) {
            val selectorExpression = dotQualified.selectorExpression ?: continue
            val calleeReference = when (selectorExpression) {
                is KtNameReferenceExpression -> selectorExpression
                is KtCallExpression -> selectorExpression.calleeExpression as? KtNameReferenceExpression ?: continue
                else -> continue
            }
            val receiver = dotQualified.receiverExpression

            if (!isSimpleReceiverExpression(receiver)) continue

            val isUnresolved = calleeReference.diagnostics().directOnly(true)
                .map { it.diagnosticClass }
                .any { it == KaFirDiagnostic.UnresolvedReference::class || it == KaFirDiagnostic.UnresolvedReferenceWrongReceiver::class }
            if (!isUnresolved) continue

            val receiverIsValue = when (receiver) {
                is KtThisExpression -> true
                else -> (receiver as? KtResolvableCall)?.tryResolveCall()?.successful?.variable?.symbol != null
            }
            if (!receiverIsValue) continue

            return RewriteCandidate(
                codeFragment = codeFragment,
                dotQualified = SmartPointerManager.createPointer(dotQualified),
                receiver = SmartPointerManager.createPointer(receiver),
                receiverText = receiver.text,
                calleeName = Name.identifier(calleeReference.getReferencedName()),
                selectorText = selectorExpression.text,
            )
        }
        return null
    }

    private fun computeRewriteText(codeFragment: KtCodeFragment, candidate: RewriteCandidate): String? {
        val value = evaluateReceiver(codeFragment, candidate.receiver, candidate.receiverText) ?: return null

        val asmType = value.asValue().asmType
        val className = asmType.className
        val rawClassId = ClassId.fromString(asmType.internalName.replace('$', '.'))
        val runtimeClassId = JavaToKotlinClassMap.mapJavaToKotlin(rawClassId.asSingleFqName()) ?: rawClassId

        return ReadAction.nonBlocking(Callable {
            analyze(codeFragment) {
                val classSymbol = findClass(runtimeClassId)
                    ?: findLocalClassByName(codeFragment, className)?.namedClassSymbol
                    ?: return@analyze null

                buildRewriteText(classSymbol, runtimeClassId, candidate)
            }
        }).executeSynchronously()
    }

    protected fun findLocalClassByName(codeFragment: KtCodeFragment, className: String): KtClassOrObject? {
        val contextFile = codeFragment.context?.containingFile as? KtFile ?: return null
        val classNameProvider = ClassNameProvider(ClassNameProvider.Configuration.DEFAULT)
        return contextFile.collectDescendantsOfType<KtClassOrObject> { it.name != null }
            .firstOrNull { className in classNameProvider.getCandidatesForElement(it) }
    }

    context(_: KaSession)
    protected fun memberExistsOn(classSymbol: KaClassSymbol, name: Name): Boolean =
        classSymbol.memberScope.callables(name).any()

    private fun evaluateReceiver(codeFragment: KtCodeFragment, receiver: SmartPsiElementPointer<KtExpression>, receiverText: String): Value? {
        if (receiverValueCache.containsKey(receiverText)) return receiverValueCache[receiverText]

        val plainName = ReadAction.nonBlocking(Callable {
            when (val element = receiver.element) {
                is KtThisExpression -> if (element.getLabelName() == null) "" else null // "" marks unlabeled `this`
                is KtNameReferenceExpression -> element.getReferencedName()
                else -> null // qualified chain etc.: fall back to compilation
            }
        }).executeSynchronously()
        val direct: Value? = try {
            when (plainName) {
                null -> null
                "" ->
                    executionContext.evaluationContext.computeThisObject() as? ObjectReference
                        ?: executionContext.frameProxy.safeThisObject()
                else ->
                    executionContext.frameProxy.safeVisibleVariableByName(plainName)
                        ?.let { executionContext.frameProxy.getValue(it) }
            }
        } catch (_: EvaluateException) {
            null
        }
        if (direct != null) {
            receiverValueCache[receiverText] = direct
            return direct
        }

        val value = try {
            val evaluationContext = executionContext.evaluationContext
            val evaluator = ReadAction.nonBlocking(Callable {
                val receiverFragment = KtPsiFactory(codeFragment.project).createBlockCodeFragment(receiverText, codeFragment.context)
                KotlinEvaluatorBuilder.build(receiverFragment, ContextUtil.getSourcePosition(evaluationContext))
            }).executeSynchronously()
            evaluator.evaluate(evaluationContext)
        } catch (e: EvaluateException) {
            LOG.warn("Failed to evaluate receiver for runtime-type member access: $receiverText", e)
            null
        }

        receiverValueCache[receiverText] = value
        return value
    }

    private fun rewrite(dotQualified: SmartPsiElementPointer<KtDotQualifiedExpression>, newExpressionText: String) {
        invokeAndWaitIfNeeded {
            val element = dotQualified.element ?: return@invokeAndWaitIfNeeded
            element.project.executeWriteCommand(
                KotlinDebuggerEvaluationBundle.message("wrap.expression")
            ) {
                element.replace(KtPsiFactory(element.project).createExpression(newExpressionText))
            }
        }
    }

    // Caller's responsibility to run under a read action.
    protected fun reportUnexpectedState(codeFragment: KtCodeFragment, message: String) {
        if (!isApplicationInternalMode()) {
            LOG.error(message)
            return
        }
        val attachments = listOfNotNull(
            attachmentByPsiFile(codeFragment.context?.containingFile),
            attachmentByPsiFile(codeFragment),
        )
        LOG.error(message, attachments.merge())
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

    private fun isSimpleReceiverExpression(receiver: KtExpression): Boolean =
        when (receiver) {
            is KtNameReferenceExpression -> true
            is KtThisExpression -> true
            is KtDotQualifiedExpression -> {
                val selectorSimple = receiver.selectorExpression is KtNameReferenceExpression
                selectorSimple && isSimpleReceiverExpression(receiver.receiverExpression)
            }
            else -> false
        } && receiver.collectDescendantsOfType<KtCallExpression>().isEmpty()
}

/**
 * Rewrites an unresolved instance-member access against the runtime type by inserting an explicit cast,
 * e.g. `i.p` where `val i: I = B()` becomes `(i as B).p`, and `i.f()` becomes `(i as B).f()`.
 */
internal class KotlinRuntimeTypeCastWrapper(
    stats: CodeFragmentCompilationStats,
    executionContext: ExecutionContext,
) : KotlinRuntimeTypeMemberWrapper(stats, executionContext) {

    context(_: KaSession)
    override fun buildRewriteText(
        classSymbol: KaNamedClassSymbol,
        runtimeClassId: ClassId,
        candidate: RewriteCandidate,
    ): String? {
        if (!memberExistsOn(classSymbol, candidate.calleeName)) return null

        val classId = classSymbol.defaultType.symbol?.classId
        val recovered = if (classSymbol.typeParameters.isNotEmpty() && classId != null) {
            val receiver = candidate.receiver.element
            if (receiver == null) {
                reportUnexpectedState(
                    candidate.codeFragment,
                    "Receiver PSI unexpectedly unavailable while recovering cast type for '${candidate.receiverText}.${candidate.selectorText}'",
                )
                null
            } else {
                recoverCastTypeFromSource(receiver, classId)
            }
        } else {
            null
        }

        val castType = recovered
            ?: classSymbol.defaultType.replaceTypeParametersWithStarProjections()?.render(position = Variance.INVARIANT)
            ?: return null

        return "(${candidate.receiverText} as $castType).${candidate.selectorText}"
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

    context(_: KaSession)
    private fun KaType.replaceTypeParametersWithStarProjections(): KaType? =
        abbreviationOrSelf.symbol?.defaultTypeWithStarProjections
}

/**
 * Rewrites an unresolved `receiver.member` access to a static or companion member of the runtime type by
 * addressing it through the type name instead of a cast, e.g. `e.entries` where `val e: I = E.A` becomes
 * `E.entries`, and `e.c` where `c` is a companion member becomes `E.c`. A cast cannot help here: static and
 * companion members are not reachable through an instance.
 */
internal class KotlinRuntimeStaticMemberWrapper(
    stats: CodeFragmentCompilationStats,
    executionContext: ExecutionContext,
) : KotlinRuntimeTypeMemberWrapper(stats, executionContext) {

    context(_: KaSession)
    override fun buildRewriteText(
        classSymbol: KaNamedClassSymbol,
        runtimeClassId: ClassId,
        candidate: RewriteCandidate,
    ): String? {
        val name = candidate.calleeName

        // Instance members are the KotlinRuntimeTypeCastWrapper's job; only handle non-instance members here.
        if (memberExistsOn(classSymbol, name)) return null

        val isStaticOrCompanionMember = classSymbol.staticMemberScope.callables(name).any() ||
                classSymbol.companionObject?.memberScope?.callables(name)?.any() == true
        if (!isStaticOrCompanionMember) return null

        val typeName = classSymbol.classId?.asSingleFqName()?.asString() ?: return null
        return "$typeName.${candidate.selectorText}"
    }
}
