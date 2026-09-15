// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.DelegatingHeadlessRenamePsiElementProcessor
import com.intellij.refactoring.rename.RenameJavaMethodProcessor
import org.jetbrains.kotlin.asJava.canHaveSyntheticGetter
import org.jetbrains.kotlin.asJava.canHaveSyntheticSetter
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.syntheticGetter
import org.jetbrains.kotlin.idea.base.util.restrictToKotlinSources
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.load.java.JvmAbi

private val RENAME_JAVA_GETTER_MARKER = Key.create<Boolean>("RenameJavaGetterMarker")
internal fun isKotlinAwareJavaGetterRename(ref: KtReference) : Boolean = ref.element.getUserData(RENAME_JAVA_GETTER_MARKER) != null
/**
 * Renames a Java getter, and also the Kotlin references which read it as a property.
 *
 * It states the headless rename itself. The base class cannot state it: the base has subclasses, and
 * a subclass must not inherit the statement.
 */
class KotlinAwareJavaGetterRenameProcessor : RenameJavaMethodProcessor(), DelegatingHeadlessRenamePsiElementProcessor {
    override fun canProcessElement(element: PsiElement): Boolean =
        super.canProcessElement(element) && element !is KtLightMethod && (element as PsiMethod).canHaveSyntheticGetter

    /**
     * The base method and its whole hierarchy, when [element] overrides something.
     *
     * Java asks the user here whether to rename the base method. This answers that question with
     * yes, so that the code stays consistent. The default of the interface would reach the dialog,
     * because the two-parameter method of the base class asks.
     */
    override fun substituteElementToRenameHeadless(element: PsiElement): PsiElement? =
        substituteElementToRename(element, null, false)

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean
    ): Collection<PsiReference> {
        val getters = super.findReferences(element, searchScope, searchInCommentsAndStrings)
        val setters = findSetterReferences(element, searchScope, searchInCommentsAndStrings).orEmpty()
        return (getters + setters).map {
            it.element.putUserData(RENAME_JAVA_GETTER_MARKER, true)
            it
        }
    }

    private fun findSetterReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean
    ): Collection<PsiReference>? {
        val getter = element as? PsiMethod ?: return null
        val propertyName = getter.syntheticGetter ?: return null
        val containingClass = getter.containingClass ?: return null
        val setterName = JvmAbi.setterName(propertyName.asString())
        val restrictedToKotlinScope by lazy { searchScope.restrictToKotlinSources() }
        return containingClass
            .findMethodsByName(setterName, false)
            .filter { it.canHaveSyntheticSetter }
            .asSequence()
            .flatMap { super.findReferences(it, restrictedToKotlinScope, searchInCommentsAndStrings) }
            .toList()
    }
}
