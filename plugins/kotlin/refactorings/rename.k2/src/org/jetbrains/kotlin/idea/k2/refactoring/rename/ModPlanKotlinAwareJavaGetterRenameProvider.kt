// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.model.SymbolPointer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiReference
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.createSmartFileRange
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanChainBuilder
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.ElementsForRename
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.SubjectUsage
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.Substitution
import com.intellij.refactoring.modplan.rename.ModPlanRenameUtil
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinAwareJavaGetterRenameProcessor

/**
 * The rename of a Java getter that Kotlin reads as a property, as [KotlinAwareJavaGetterRenameProcessor] does it: the next provider that
 * owns the getter renames it, and this one also renames the Kotlin writes of the property, which refer to its setter. Registered first, as
 * the legacy processor.
 *
 * The legacy processor extends the processor of the Java methods and adds the setter references to its search. The Java provider does
 * not know these references, so this provider adds them to the usages and writes them:
 * ```
 * // Java
 * public class A { public int getX() { ... } public void setX(int x) { ... } }
 * // Kotlin
 * fun f(a: A) { a.x = a.x + 1 }   // rename getX to getY
 * // after, in Kotlin
 * fun f(a: A) { a.y = a.y + 1 }
 * ```
 */
@ApiStatus.Internal
class ModPlanKotlinAwareJavaGetterRenameProvider : ModPlanRenameProvider {
    private val processor = KotlinAwareJavaGetterRenameProcessor()

    override fun isOwner(subject: SymbolPointer): Boolean {
        val element = subject.element() ?: return false
        return processor.canProcessElement(element) && delegateOf(subject) != null
    }

    /** As the legacy processor, which extends the one of the Java methods. */
    override fun substituteRequest(chainBuilder: ModPlanChainBuilder, subject: SymbolPointer, newName: String): ModPlanChain<Substitution?> {
        val delegate = delegateOf(subject) ?: return chainBuilder.passValue(null)
        return delegate.substituteRequest(chainBuilder, subject, newName)
    }

    override fun renamers(
        subject: SymbolPointer,
        elementsForRename: ElementsForRename,
        factories: List<AutomaticRenamerFactory>,
        usages: List<SubjectUsage>,
    ): List<AutomaticRenamer> =
        delegateOf(subject)?.renamers(subject, elementsForRename, factories, usages.filter { it !is SetterUsage }).orEmpty()

    /**
     * The usages of the delegate, then the Kotlin writes of the property, which refer to the setter, as `findSetterReferences` of the
     * processor finds them. The delegate also finds the setter references, so a place of a setter reference keeps only its first usage of
     * the delegate: the getter reference.
     */
    override fun findUsages(subject: SymbolPointer, elementsForRename: ElementsForRename, request: ModPlanRequest): List<SubjectUsage> {
        val delegate = delegateOf(subject) ?: return emptyList()
        val setterUsages = subject.element()
            ?.let { getter -> processor.findSetterReferences(getter, request.searchScope().get(), false) }.orEmpty()
            .map { reference -> SetterUsage(reference, reference.element.createSmartFileRange(reference.rangeInElement)) }
        val setterPlaces = setterUsages.mapNotNullTo(HashSet()) { it.place()?.let(::keyOf) }
        val seenSetterPlaces = HashSet<Triple<VirtualFile?, Int, Int>?>()
        val delegateUsages = delegate.findUsages(subject, elementsForRename, request).filter { usage ->
            val key = usage.place()?.let(::keyOf)
            key !in setterPlaces || seenSetterPlaces.add(key)
        }
        return delegateUsages + setterUsages
    }

    /**
     * The analysis of the delegate, and the setter references get the new name, as the legacy processor gives the new name of the getter to
     * every reference: a Kotlin reference takes the property name. A setter reference at the place of a getter reference, as in `a.x++`, is
     * written by the getter reference.
     */
    override fun analyze(
        subject: SymbolPointer,
        elementsForRename: ElementsForRename,
        keptUsages: List<SubjectUsage>,
    ): ModPlanAnalysisResult {
        val delegate = delegateOf(subject) ?: return ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE)
        val delegateUsages = keptUsages.filter { it !is SetterUsage }
        val result = delegate.analyze(subject, elementsForRename, delegateUsages)
        val newName = elementsForRename.newNameOf(subject)
        if (result !is ModPlanAnalysisResult.Found || newName == null) return result
        val delegatePlaces = delegateUsages.mapNotNullTo(HashSet()) { it.place() }
        val setterReferences = keptUsages.filterIsInstance<SetterUsage>().filter { it.place() !in delegatePlaces }
            .map { MoveRenameUsageInfo(it.reference, null) }
        val setterEdits = ModPlanRenameUtil.referenceEdits(setterReferences, newName)
                          ?: return ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED)
        return ModPlanAnalysisResult.Found(result.edits + setterEdits, result.conflicts)
    }

    /** A Kotlin write of the property, which refers to the setter. */
    private class SetterUsage(val reference: PsiReference, private val place: SmartPsiFileRange?) : SubjectUsage {
        override fun place(): SmartPsiFileRange? = place
    }

    private fun keyOf(place: SmartPsiFileRange): Triple<VirtualFile?, Int, Int>? =
        place.range?.let { range -> Triple(place.virtualFile, range.startOffset, range.endOffset) }

    /** @return the provider that renames [subject] if this one did not exist: the next one in the extension order that owns it */
    private fun delegateOf(subject: SymbolPointer): ModPlanRenameProvider? =
        ModPlanRenameProvider.EP_NAME.findFirstSafe { it !is ModPlanKotlinAwareJavaGetterRenameProvider && it.isOwner(subject) }
}
