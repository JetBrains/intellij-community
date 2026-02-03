// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureHandler
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.K2IntroduceVariableHandler
import org.jetbrains.kotlin.idea.k2.refactoring.introduceConstant.KotlinIntroduceConstantHandler
import org.jetbrains.kotlin.idea.k2.refactoring.introduceParameter.KotlinFirIntroduceLambdaParameterHandler
import org.jetbrains.kotlin.idea.k2.refactoring.introduceParameter.KotlinFirIntroduceParameterHandler
import org.jetbrains.kotlin.idea.k2.refactoring.introduceProperty.KotlinIntroducePropertyHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractInterfaceHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractSuperclassHandler
import org.jetbrains.kotlin.idea.refactoring.pullUp.KotlinPullUpHandler
import org.jetbrains.kotlin.idea.refactoring.pushDown.KotlinPushDownHandler

class KotlinFirRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isSafeDeleteAvailable(element: PsiElement): Boolean = element.canDeleteElement()

    /**
     * @see org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSupportProvider.isInplaceRenameAvailable
     */
    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean = false

    /**
     * @see org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSupportProvider.isMemberInplaceRenameAvailable
     */
    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean = false

    /**
     * @see org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSupportProvider.getIntroduceVariableHandler
     */
    override fun getIntroduceVariableHandler(): RefactoringActionHandler = K2IntroduceVariableHandler

    override fun getChangeSignatureHandler(): ChangeSignatureHandler = KotlinChangeSignatureHandler

    override fun getIntroduceParameterHandler(): RefactoringActionHandler = KotlinFirIntroduceParameterHandler()

    override fun getIntroduceFunctionalParameterHandler(): RefactoringActionHandler = KotlinFirIntroduceLambdaParameterHandler()

    override fun getIntroduceConstantHandler(): RefactoringActionHandler = KotlinIntroduceConstantHandler()

    fun getIntroducePropertyHandler(): RefactoringActionHandler = KotlinIntroducePropertyHandler()

    override fun getPushDownHandler(): RefactoringActionHandler = KotlinPushDownHandler()

    override fun getPullUpHandler(): RefactoringActionHandler = KotlinPullUpHandler()

    override fun getExtractInterfaceHandler(): RefactoringActionHandler = KotlinExtractInterfaceHandler

    override fun getExtractSuperClassHandler(): RefactoringActionHandler = KotlinExtractSuperclassHandler
}