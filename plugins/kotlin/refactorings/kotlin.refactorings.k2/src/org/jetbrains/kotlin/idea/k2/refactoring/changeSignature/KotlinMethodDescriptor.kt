// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.refactoring.changeSignature.MethodDescriptor.ReadWriteOption
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.changeSignature.toValVar
import org.jetbrains.kotlin.idea.search.ExpectActualUtils
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.types.Variance

class KotlinMethodDescriptor(c: KtNamedDeclaration) : KotlinModifiableMethodDescriptor<KotlinParameterInfo, Visibility> {
    private val callable: KtNamedDeclaration = findTargetCallable(c)

    private fun findTargetCallable(c: KtNamedDeclaration): KtNamedDeclaration {

        fun getCallable(): KtNamedDeclaration = (ExpectActualUtils.liftToExpect(c) ?: c) as KtNamedDeclaration

        return if (ApplicationManager.getApplication().isDispatchThread && !ApplicationManager.getApplication().isWriteAccessAllowed) {
            runWithModalProgressBlocking(
                c.project, KotlinBundle.message("fix.change.signature.prepare")
            ) { readAction { getCallable() } }
        } else {
            getCallable()
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class, KaExperimentalApi::class)
    internal val oldReturnType: String = allowAnalysisOnEdt {
        analyze(callable) {
            (callable as? KtCallableDeclaration)?.returnType?.render(position = Variance.INVARIANT) ?: ""
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class, KaExperimentalApi::class)
    internal val oldReceiverType: String? = allowAnalysisOnEdt {
        analyze(callable) {
            (callable as? KtCallableDeclaration)?.receiverTypeReference?.type?.render(position = Variance.INVARIANT)
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override var receiver: KotlinParameterInfo? = (callable as? KtCallableDeclaration)?.receiverTypeReference?.let {
        allowAnalysisOnEdt {
            analyze(callable) {
                val ktType = it.type
                val nameValidator = KotlinDeclarationNameValidator(
                    callable,
                    true,
                    KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
                )
                val receiverName = with(KotlinNameSuggester()) {
                    suggestTypeNames(ktType).map { typeName ->
                        KotlinNameSuggester.suggestNameByName(typeName) { nameValidator.validate(it) }
                    }
                }.firstOrNull() ?: "receiver"

                KotlinParameterInfo(
                    originalIndex = 0,
                    originalType = KotlinTypeInfo(ktType, callable),
                    name = receiverName,
                    valOrVar = KotlinValVar.None,
                    defaultValueForCall = null,
                    defaultValueAsDefaultParameter = false,
                    defaultValue = null,
                    context = callable
                )
            }
        }
    }

    private val parameters: MutableList<KotlinParameterInfo>

    init {
        @OptIn(KaAllowAnalysisOnEdt::class)
        parameters = allowAnalysisOnEdt {
            analyze(callable) {
                val params = mutableListOf< KotlinParameterInfo>()
                receiver?.let { params.add(it) }
                (callable as? KtCallableDeclaration)
                    ?.valueParameters?.forEach { p ->
                        val parameterInfo = KotlinParameterInfo(
                            originalIndex = params.size,
                            originalType = KotlinTypeInfo(p.returnType, callable),
                            name = p.name ?: "",
                            valOrVar = p.valOrVarKeyword.toValVar(),
                            defaultValueForCall = p.defaultValue,
                            defaultValueAsDefaultParameter = p.defaultValue != null,
                            defaultValue = p.defaultValue,
                            context = callable
                        )
                        params.add(parameterInfo)
                    }

                params
            }
        }
    }
    override fun getName(): String {
        return callable.name ?: ""
    }

    override fun getParameters(): MutableList<KotlinParameterInfo> {
         return parameters
    }

    override fun getParametersCount(): Int {
        return (callable as? KtCallableDeclaration)?.valueParameters?.size ?: 0
    }

    @OptIn(KaAllowAnalysisOnEdt::class, KaExperimentalApi::class)
    private val _visibility = allowAnalysisOnEdt {
        analyze(callable) {
            callable.symbol.compilerVisibility
        }
    }

    override fun getVisibility(): Visibility = _visibility

    override fun getMethod(): KtNamedDeclaration {
        return callable
    }

    override fun canChangeVisibility(): Boolean {
        return when {
          callable is KtFunction && callable.isLocal -> false
          (callable.containingClassOrObject as? KtClass)?.isInterface() == true -> false
          callable is KtConstructor<*> && (callable.containingClassOrObject as? KtClass)?.isEnum() == true -> false
          callable is KtClass && callable.isEnum() -> false
          else -> true
        }
    }

    override fun canChangeParameters(): Boolean {
        return true
    }

    override fun canChangeName(): Boolean {
        return callable is KtNamedFunction
    }

    override fun canChangeReturnType(): ReadWriteOption {
        return if (callable is KtConstructor<*> || callable is KtClass) ReadWriteOption.None else ReadWriteOption.ReadWrite
    }

    override val original: KotlinModifiableMethodDescriptor<KotlinParameterInfo, Visibility>
        get() = this

    override val baseDeclaration: KtNamedDeclaration = callable
}