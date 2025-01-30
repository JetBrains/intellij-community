// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.ConflictsUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.willBeMoved
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import java.util.*

internal fun PsiElement.getUsageContext(): PsiElement {
    return when (this) {
        is KtElement -> PsiTreeUtil.getParentOfType(
            this,
            KtPropertyAccessor::class.java,
            KtProperty::class.java,
            KtNamedFunction::class.java,
            KtConstructor::class.java,
            KtClassOrObject::class.java
        ) ?: containingFile
        else -> ConflictsUtil.getContainer(this)
    }
}

internal fun checkInternalMemberUsages(
    elementsToMove: Collection<KtNamedDeclaration>,
    targetDir: PsiDirectory
): MultiMap<PsiElement, String> {
    val targetModule = targetDir.module ?: return MultiMap.empty()
    val conflicts = MultiMap<PsiElement, String>()

    val membersToCheck = LinkedHashSet<KtDeclaration>()
    val memberCollector = object : KtVisitorVoid() {
        override fun visitClassOrObject(classOrObject: KtClassOrObject) {
            val declarations = classOrObject.declarations
            declarations.filterTo(membersToCheck) { it.hasModifier(KtTokens.INTERNAL_KEYWORD) }
            declarations.forEach { it.accept(this) }
        }
    }
    elementsToMove.forEach { it.accept(memberCollector) }

    for (memberToCheck in membersToCheck) {
        for (reference in ReferencesSearch.search(memberToCheck)) {
            val element = reference.element
            val usageModule = ModuleUtilCore.findModuleForPsiElement(element) ?: continue
            if (usageModule != targetModule && targetModule !in usageModule.implementedModules && !element.willBeMoved(elementsToMove)) {
                val container = element.getUsageContext()
                val message = KotlinBundle.message(
                    "text.0.uses.internal.1.which.will.be.inaccessible.after.move",
                    RefactoringUIUtil.getDescription(container, false),
                    RefactoringUIUtil.getDescription(memberToCheck, false)
                ).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                conflicts.putValue(element, message)
            }
        }
    }
    return conflicts
}