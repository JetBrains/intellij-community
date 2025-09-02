// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.ide.util.SuperMethodWarningUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.intellij.refactoring.rename.RenameJavaMethodProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name

class RenameJavaSyntheticPropertyHandler : AbstractReferenceSubstitutionRenameHandler() {
    class Processor : RenamePsiElementProcessor() {
        override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
            val propertyWrapper = element as? SyntheticPropertyWrapper ?: return

            propertyWrapper.getter.let { getter ->
                val getterName = JvmAbi.getterName(newName)
                allRenames[getter] = getterName
                RenameJavaMethodProcessor().prepareRenaming(getter, getterName, allRenames, scope)
            }

            propertyWrapper.setter?.let { setter ->
                val setterName = JvmAbi.setterName(newName)
                allRenames[setter] = setterName
                RenameJavaMethodProcessor().prepareRenaming(setter, setterName, allRenames, scope)
            }
        }

        override fun substituteElementToRename(
            element: PsiElement,
            editor: Editor?
        ): PsiElement {
            if (element is SyntheticPropertyWrapper) {
            val superMethod = SuperMethodWarningUtil.checkSuperMethod(element.getter)
            val setter = element.setter
            return SyntheticPropertyWrapper(element.manager,
                                            superMethod,
                                            setter?.let { it.findSuperMethods(superMethod.containingClass).firstOrNull() } ?: element.setter,
                                            element.name)
            }
            return element
        }

        override fun canProcessElement(element: PsiElement) = element is SyntheticPropertyWrapper
    }

    class SyntheticPropertyWrapper(
        manager: PsiManager,
        val getter: PsiMethod,
        val setter: PsiMethod?,
        val name: Name
    ) : LightElement(manager, KotlinLanguage.INSTANCE), PsiNamedElement {

        override fun getContainingFile() = getter.containingFile

        override fun getName() = name.asString()

        override fun setName(name: String): PsiElement {
            return this
        }

        override fun toString(): String {
            return PsiFormatUtil.formatMethod(getter, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME, PsiFormatUtilBase.SHOW_NAME) +
                   "|" +
                   (setter?.let {
                       PsiFormatUtil.formatMethod(it, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME, PsiFormatUtilBase.SHOW_NAME)
                   } ?: name.asString())
        }
    }

    override fun getElementToRename(dataContext: DataContext): PsiElement? {
        val refExpr = getReferenceExpression(dataContext) ?: return null
        @OptIn(KaAllowAnalysisOnEdt::class)
        allowAnalysisOnEdt {
            analyze(refExpr) {
              val symbol = refExpr.mainReference.resolveToSymbol() as? KaSyntheticJavaPropertySymbol
                      ?: return null

                val getter = symbol.javaGetterSymbol.psi as? PsiMethod ?: return null
                return SyntheticPropertyWrapper(refExpr.manager,
                                                getter,
                                                symbol.javaSetterSymbol?.psi as? PsiMethod,
                                                symbol.name)
            }
        }
    }
}
