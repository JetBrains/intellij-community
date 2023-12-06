// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.MethodDescriptor.ReadWriteOption
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.changeSignature.toValVar
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.types.Variance

class KotlinMethodDescriptor(private val callable: KtNamedDeclaration) :
    KotlinModifiableMethodDescriptor<KotlinParameterInfo, Visibility> {

    @OptIn(KtAllowAnalysisOnEdt::class)
    internal val oldReturnType: String = allowAnalysisOnEdt {
        analyze(callable) {
            (callable as? KtCallableDeclaration)?.getReturnKtType()?.render(position = Variance.INVARIANT) ?: ""
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    internal val oldReceiverType: String? = allowAnalysisOnEdt {
        analyze(callable) {
            (callable as? KtCallableDeclaration)?.receiverTypeReference?.getKtType()?.render(position = Variance.INVARIANT)
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override var receiver: KotlinParameterInfo? = (callable as? KtCallableDeclaration)?.receiverTypeReference?.let {
        allowAnalysisOnEdt {
            analyze(callable) {
                val ktType = it.getKtType()
                val nameValidator = KotlinDeclarationNameValidator(
                    callable,
                    true,
                    KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
                    this,
                )
                val receiverName = with(KotlinNameSuggester()) {
                    suggestTypeNames(ktType).map { typeName ->
                        KotlinNameSuggester.suggestNameByName(typeName, nameValidator)
                    }
                }.firstOrNull() ?: "receiver"

                KotlinParameterInfo(
                    0, KotlinTypeInfo(ktType.render(position = Variance.INVARIANT), callable), receiverName,
                    KotlinValVar.None, null, false, null, callable
                )
            }
        }
    }

    private val parameters: MutableList<KotlinParameterInfo>

    init {
        @OptIn(KtAllowAnalysisOnEdt::class)
        parameters = allowAnalysisOnEdt {
            analyze(callable) {
                val params = mutableListOf< KotlinParameterInfo>()
                receiver?.let { params.add(it) }
                (callable as? KtCallableDeclaration)
                    ?.valueParameters?.forEach { p ->
                        val parameterInfo = KotlinParameterInfo(
                            params.size, KotlinTypeInfo(p.getReturnKtType().render(position = Variance.INVARIANT), callable),
                            p.name!!,
                            p.valOrVarKeyword.toValVar(),
                            null, false, p.defaultValue, callable
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

    @OptIn(KtAllowAnalysisOnEdt::class)
    private val _visibility = allowAnalysisOnEdt {
        analyze(callable) {
            (callable.getSymbol() as? KtSymbolWithVisibility)?.visibility ?: Visibilities.Public
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