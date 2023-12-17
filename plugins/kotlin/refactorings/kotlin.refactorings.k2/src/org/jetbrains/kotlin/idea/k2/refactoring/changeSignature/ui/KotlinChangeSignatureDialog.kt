// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.ui


import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.changeSignature.CallerChooserBase
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import com.intellij.refactoring.ui.VisibilityPanelBase
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.codeinsight.utils.AddQualifiersUtil
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.*
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableMethodDescriptor.Kind
import org.jetbrains.kotlin.idea.refactoring.changeSignature.ui.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

@OptIn(KtAllowAnalysisOnEdt::class)
internal class KotlinChangeSignatureDialog(
    project: Project,
    editor: Editor?,
    methodDescriptor: KotlinMethodDescriptor,
    context: PsiElement,
    @NlsContexts.Command private val commandName: String?
) : KotlinBaseChangeSignatureDialog<KotlinParameterInfo, Visibility, KotlinMethodDescriptor>(
    project,
    editor,
    methodDescriptor,
    context,
    commandName
) {
    override fun PsiCodeFragment?.isValidType(): Boolean {
        if (this !is KtCodeFragment) return false
        val typeRef = findChildByClass(KtExpression::class.java) ?: return false
        allowAnalysisOnEdt {
            analyze(typeRef) {
                val ktType = typeRef.getKtType()
                return ktType !is KtErrorType
            }
        }
    }

    override fun validateButtons() {
        validateButtonsAsync()
    }

    override fun createParametersInfoModel(method: KotlinMethodDescriptor): KotlinCallableParameterTableModel<KotlinParameterInfo, Visibility> {
        fun createRowItemInner(
            parameterInfo: KotlinParameterInfo?,
            typeContext: PsiElement,
            defaultValueContext: PsiElement
        ): ParameterTableModelItemBase<KotlinParameterInfo> {
            val resultParameterInfo = parameterInfo
                ?: KotlinParameterInfo(
                    -1,
                    KotlinTypeInfo("", context = method.method),
                    "",
                    defaultValOrVar(method.method),
                    null,
                    false,
                    null,
                    method.method
                )

            val psiFactory = KtPsiFactory(myDefaultValueContext.project)
            val paramTypeCodeFragment: PsiCodeFragment = psiFactory.createExpressionCodeFragment(
                resultParameterInfo.typeText,
                typeContext,
            )

            val defaultValueCodeFragment: PsiCodeFragment = psiFactory.createExpressionCodeFragment(
                resultParameterInfo.defaultValueForCall?.text ?: "",
                defaultValueContext,
            )

            return object : ParameterTableModelItemBase<KotlinParameterInfo>(
                resultParameterInfo,
                paramTypeCodeFragment,
                defaultValueCodeFragment,
            ) {
                override fun isEllipsisType(): Boolean = false
            }
        }
        return when (method.kind) {
            Kind.FUNCTION -> object : KotlinFunctionParameterTableModel<KotlinParameterInfo, Visibility>(method, myDefaultValueContext) {
                override fun createRowItem(parameterInfo: KotlinParameterInfo?): ParameterTableModelItemBase<KotlinParameterInfo> {
                    return createRowItemInner(parameterInfo, myTypeContext, myDefaultValueContext)
                }
            }

            Kind.PRIMARY_CONSTRUCTOR -> object :
                KotlinPrimaryConstructorParameterTableModel<KotlinParameterInfo, Visibility>(method, myDefaultValueContext) {
                override fun createRowItem(parameterInfo: KotlinParameterInfo?): ParameterTableModelItemBase<KotlinParameterInfo> {
                    return createRowItemInner(parameterInfo, myTypeContext, myDefaultValueContext)
                }
            }

            Kind.SECONDARY_CONSTRUCTOR -> object :
                KotlinSecondaryConstructorParameterTableModel<KotlinParameterInfo, Visibility>(method, myDefaultValueContext) {
                override fun createRowItem(parameterInfo: KotlinParameterInfo?): ParameterTableModelItemBase<KotlinParameterInfo> {
                    return createRowItemInner(parameterInfo, myTypeContext, myDefaultValueContext)
                }
            }
        }
    }


    override fun createRefactoringProcessor(): BaseRefactoringProcessor {
        val changeInfo = evaluateChangeSignatureInfo(false)
        return KotlinChangeSignatureProcessor(project, changeInfo)
    }

    private fun evaluateChangeSignatureInfo(forPreview: Boolean): KotlinChangeInfo {
        val callable = myMethod.method
        var receiverInfo: KotlinParameterInfo? = null
        val parameters = parametersTableModel.items.map { parameter ->
            val parameterInfo = parameter.parameter
            if (parameter.isReceiverIn(parametersTableModel)) {
                parameterInfo.defaultValueAsDefaultParameter = false
                receiverInfo = parameterInfo
            }

            parameterInfo.setType(parameter.typeCodeFragment.text)

            val codeFragment = parameter.defaultValueCodeFragment as KtExpressionCodeFragment

            if (!forPreview) AddQualifiersUtil.addQualifiersRecursively(codeFragment)

            val oldDefaultValue = parameterInfo.defaultValueForCall
            if (codeFragment.text != (if (oldDefaultValue != null) oldDefaultValue.text else "")) {
                parameterInfo.defaultValueForCall = codeFragment.getContentElement()
            }
            if (parameter.parameter.defaultValueAsDefaultParameter) {
                parameterInfo.defaultValue = codeFragment.getContentElement()
            }
            parameterInfo
        }

        val parametersWithReceiverInFirstPosition = if (parametersTableModel.receiver != null)
            parameters.sortedByDescending { it == parametersTableModel.receiver }
        else
            parameters
        val methodDescriptor = KotlinMethodDescriptor(callable)
        return KotlinChangeInfo(
            methodDescriptor,
            name = methodName,
            parameterInfos = parametersWithReceiverInFirstPosition,
            receiver = receiverInfo,
            aNewVisibility = myVisibilityPanel.visibility ?: methodDescriptor.visibility,
            newReturnTypeInfo = KotlinTypeInfo(myReturnTypeCodeFragment?.text, callable)
        )
    }

    override fun createReturnTypeCodeFragment(): PsiCodeFragment {
        val method = myMethod.method
        return KtPsiFactory(project).createExpressionCodeFragment(
            allowAnalysisOnEdt {
                analyze(method) {
                    method.getReturnKtType().render(position = Variance.INVARIANT)
                }
            },
            KotlinCallableParameterTableModel.getTypeCodeFragmentContext(myMethod.baseDeclaration)
        )
    }

    //don't implement propagation
    override fun createCallerChooser(
        title: @Nls String?,
        treeToReuse: Tree?,
        callback: Consumer<in MutableSet<PsiElement>>?
    ): CallerChooserBase<PsiElement?>? = null

    override fun mayPropagateParameters(): Boolean {
        return false
    }

    override fun createParametersListTable(): ParametersListTable {
        myPropagateParamChangesButton.isEnabled = false
        myPropagateParamChangesButton.isVisible = false
        return super.createParametersListTable()
    }

    override fun calculateSignature(): String {
        val changeSignatureInfo = evaluateChangeSignatureInfo(true)
        return changeSignatureInfo.getNewSignature()
    }

    private fun KotlinChangeInfo.getNewSignature(): String {
        val buffer = StringBuilder()
        val isCustomizedVisibility = visibility != Visibilities.DEFAULT_VISIBILITY

        if (methodDescriptor.kind == Kind.PRIMARY_CONSTRUCTOR) {
            buffer.append(newName)

            if (isCustomizedVisibility) {
                buffer.append(' ').append(visibility).append(" constructor ")
            }
        } else {
            if (!KtPsiUtil.isLocal(methodDescriptor.method) && isCustomizedVisibility) {
                buffer.append(visibility).append(' ')
            }

            buffer.append(if (methodDescriptor.kind == Kind.SECONDARY_CONSTRUCTOR) KtTokens.CONSTRUCTOR_KEYWORD else KtTokens.FUN_KEYWORD).append(' ')

            if (methodDescriptor.kind == Kind.FUNCTION) {
                receiverParameterInfo?.let {
                    buffer.append(it.originalType.text).append('.')
                }
                buffer.append(newName)
            }
        }

        if (method !is KtVariableDeclaration) {
            buffer.append("(").append(getNewParametersSignatureWithoutParentheses(null, method, false)).append(")")
        }

        if (methodDescriptor.kind == Kind.FUNCTION) {
            //keep type alias in signature, otherwise it's unclear why it was created
            myReturnTypeCodeFragment?.text?.takeUnless { it == "Unit" || it == "kotlin.Unit" }?. let {buffer.append(": ").append(it) }
        }

        return buffer.toString()
    }

    override fun createVisibilityControl(): VisibilityPanelBase<Visibility> = ComboBoxVisibilityPanel(
        arrayOf(
            Visibilities.Internal,
            Visibilities.Private,
            Visibilities.Protected,
            Visibilities.Public
        )
    )
}