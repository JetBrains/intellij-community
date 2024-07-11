// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiVariable
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.psi.unquoteKotlinIdentifier
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class AutomaticVariableRenamer(
    klass: PsiNamedElement, // PsiClass or JetClass
    newClassName: String,
    usages: Collection<UsageInfo>
) : AutomaticRenamer() {
    private val toUnpluralize = ArrayList<KtNamedDeclaration>()

    init {
        val oldClassName = klass.name!!.unquoteKotlinIdentifier()
        val newClassNameUnquoted = newClassName.unquoteKotlinIdentifier()
        for (usage in usages) {
            val usageElement = usage.element ?: continue

            val parameterOrVariable = PsiTreeUtil.getParentOfType(
                usageElement,
                KtVariableDeclaration::class.java,
                KtParameter::class.java
            ) as KtCallableDeclaration? ?: continue

            val variableName = parameterOrVariable.name ?: continue

            if (variableName.equals(newClassNameUnquoted, ignoreCase = true)) continue
            if (!StringUtil.containsIgnoreCase(variableName, oldClassName)) continue

            if (parameterOrVariable.isCollectionLikeOf(klass)) {
                toUnpluralize.add(parameterOrVariable)
            }

            myElements.add(parameterOrVariable)
        }

        suggestAllNames(oldClassName, newClassNameUnquoted)
    }

    override fun getDialogTitle() = JavaRefactoringBundle.message("rename.variables.title")

    override fun getDialogDescription() = JavaRefactoringBundle.message("title.rename.variables.with.the.following.names.to")

    override fun entityName() = JavaRefactoringBundle.message("entity.name.variable")

    override fun nameToCanonicalName(name: String, element: PsiNamedElement): String {
        if (element !is KtNamedDeclaration) return name

        val codeStyleManager = JavaCodeStyleManager.getInstance(element.project)
        val kind = variableKind(codeStyleManager, element)
        val propertyName = if (kind != null) {
            codeStyleManager.variableNameToPropertyName(name, kind)
        } else name

        if (element in toUnpluralize) {
            val singular = StringUtil.unpluralize(propertyName)
            if (singular != null) return singular
            toUnpluralize.remove(element)
        }
        return propertyName
    }

    override fun canonicalNameToName(canonicalName: String, element: PsiNamedElement): String {
        if (element !is KtNamedDeclaration) return canonicalName

        val codeStyleManager = JavaCodeStyleManager.getInstance(element.project)
        val kind = variableKind(codeStyleManager, element)
        val varName = if (kind != null) {
            codeStyleManager.propertyNameToVariableName(canonicalName, kind)
        } else canonicalName

        return if (element in toUnpluralize)
            StringUtil.pluralize(varName)
        else
            varName
    }

    private fun variableKind(
        codeStyleManager: JavaCodeStyleManager,
        ktElement: KtNamedDeclaration
    ): VariableKind? {
        if (ktElement is KtProperty && ktElement.isTopLevel && !ktElement.hasModifier(KtTokens.CONST_KEYWORD)) {
            return null
        }
        if (ktElement.containingClassOrObject is KtObjectDeclaration) {
            return null
        }
        val psiVariable = ktElement.toLightElements().firstIsInstanceOrNull<PsiVariable>()
        return if (psiVariable != null) codeStyleManager.getVariableKind(psiVariable) else null
    }

    companion object {
        val LOG = Logger.getInstance(AutomaticVariableRenamer::class.java)
    }
}

private fun KtCallableDeclaration.isCollectionLikeOf(classPsiElement: PsiNamedElement): Boolean {
    analyze(this) {
        fun KaType.isCollectionLikeOf(classPsiElement: PsiNamedElement): Boolean {
            if (isArrayOrPrimitiveArray || isClassType(StandardClassIds.Collection) || allSupertypes.any { it.isClassType(StandardClassIds.Collection) }) {
                val typeArgument = (this as? KaClassType)?.typeArguments?.singleOrNull()?.type ?: return false
                if (typeArgument.expandedSymbol?.psi == classPsiElement) {
                    return true
                }
                return typeArgument.isCollectionLikeOf(classPsiElement)
            }
            return false
        }

        return returnType.isCollectionLikeOf(classPsiElement)
    }
}


open class AutomaticVariableRenamerFactory : AutomaticRenamerFactory {
    override fun isApplicable(element: PsiElement) = element is KtClass || element is KtTypeAlias

    override fun createRenamer(element: PsiElement, newName: String, usages: Collection<UsageInfo>) =
        AutomaticVariableRenamer(element as PsiNamedElement, newName, usages)

    override fun isEnabled() = KotlinCommonRefactoringSettings.getInstance().renameVariables
    override fun setEnabled(enabled: Boolean) {
        KotlinCommonRefactoringSettings.getInstance().renameVariables = enabled
    }

    override fun getOptionName(): String? = JavaRefactoringBundle.message("rename.variables")
}

class AutomaticVariableRenamerFactoryForJavaClass : AutomaticVariableRenamerFactory() {
    override fun isApplicable(element: PsiElement) = element is PsiClass

    override fun getOptionName(): String? = null
}

class AutomaticVariableInJavaRenamerFactory : AutomaticRenamerFactory {
    override fun isApplicable(element: PsiElement) = element is KtClass && element.toLightClass() != null

    override fun createRenamer(element: PsiElement, newName: String, usages: Collection<UsageInfo>) =
        // Using java variable renamer for java usages
        com.intellij.refactoring.rename.naming.AutomaticVariableRenamer((element as KtClass).toLightClass()!!, newName, usages)

    override fun isEnabled() = KotlinCommonRefactoringSettings.getInstance().renameVariables
    override fun setEnabled(enabled: Boolean) {
        KotlinCommonRefactoringSettings.getInstance().renameVariables = enabled
    }

    override fun getOptionName() = null
}