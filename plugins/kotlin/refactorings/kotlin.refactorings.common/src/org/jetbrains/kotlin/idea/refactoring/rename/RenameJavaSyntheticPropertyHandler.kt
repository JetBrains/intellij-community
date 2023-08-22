// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name

class RenameJavaSyntheticPropertyHandler : AbstractReferenceSubstitutionRenameHandler() {
    class Processor : RenamePsiElementProcessor() {
        override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
            val propertyWrapper = element as? SyntheticPropertyWrapper ?: return
            propertyWrapper.getter?.let { allRenames[it] = JvmAbi.getterName(newName) }
            propertyWrapper.setter?.let { allRenames[it] = JvmAbi.setterName(newName) }
        }

        override fun canProcessElement(element: PsiElement) = element is SyntheticPropertyWrapper
    }

    class SyntheticPropertyWrapper(
        manager: PsiManager,
        val getter: PsiMethod?,
        val setter: PsiMethod?,
        val name: Name
    ) : LightElement(manager, KotlinLanguage.INSTANCE), PsiNamedElement {

        override fun getContainingFile() = getter?.containingFile

        override fun getName() = name.asString()

        override fun setName(name: String): PsiElement {
            return this
        }

        override fun toString(): String {
            return (getter?.let {
                PsiFormatUtil.formatMethod(it, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME, PsiFormatUtilBase.SHOW_NAME)
            } ?: name.asString()) +
                   "|" +
                   (setter?.let {
                       PsiFormatUtil.formatMethod(it, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME, PsiFormatUtilBase.SHOW_NAME)
                   } ?: name.asString())
        }
    }

    override fun getElementToRename(dataContext: DataContext): PsiElement? {
        val refExpr = getReferenceExpression(dataContext) ?: return null
        @OptIn(KtAllowAnalysisOnEdt::class)
        allowAnalysisOnEdt {
            analyze(refExpr) {
              val symbol = refExpr.mainReference.resolveToSymbol() as? KtSyntheticJavaPropertySymbol
                      ?: return null

                return SyntheticPropertyWrapper(refExpr.manager,
                                                symbol.javaGetterSymbol.psi as? PsiMethod,
                                                symbol.javaSetterSymbol?.psi as? PsiMethod,
                                                symbol.name)
            }
        }
    }
}
