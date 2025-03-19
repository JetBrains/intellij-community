// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandWithContext
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.InspectionLikeProcessingForElement


internal inline fun <reified E : PsiElement, I : SelfTargetingRangeIntention<E>> intentionBasedProcessing(
    intention: I,
    writeActionNeeded: Boolean = true,
    noinline additionalChecker: (E) -> Boolean = { true }
) = object : InspectionLikeProcessingForElement<E>(E::class.java) {
    override fun isApplicableTo(element: E, settings: ConverterSettings): Boolean =
        intention.applicabilityRange(element) != null
                && additionalChecker(element)

    override fun apply(element: E) {
        intention.applyTo(element, null)
    }

    override val writeActionNeeded = writeActionNeeded
}

internal inline fun <reified E : PsiElement, I : PsiBasedModCommandAction<E>> modCommandBasedProcessing(
    modCommandAction: I,
    writeActionNeeded: Boolean = true,
    noinline additionalChecker: (E) -> Boolean = { true }
) = object : InspectionLikeProcessingForElement<E>(E::class.java) {
    override fun isApplicableTo(element: E, settings: ConverterSettings): Boolean =
        additionalChecker(element) && modCommandAction.getPresentation(context(element)) != null

    override fun apply(element: E) {
        val context = context(element)
        val perform = modCommandAction.perform(context)
        val commandWithContext = ModCommandWithContext(context, perform)
        commandWithContext.executeInBatch()
    }

    private fun context(element: E): ActionContext =
        ActionContext(element.project, element.containingFile, element.textRange.startOffset, element.textRange, element)

    override val writeActionNeeded = writeActionNeeded
}

internal inline fun <reified E : PsiElement, I : AbstractApplicabilityBasedInspection<E>> inspectionBasedProcessing(
    inspection: I,
    writeActionNeeded: Boolean = true,
    checkInspectionIsEnabled: Boolean = true,
    noinline additionalChecker: (E) -> Boolean = { true }
) = object : InspectionLikeProcessingForElement<E>(E::class.java) {
    override fun isApplicableTo(element: E, settings: ConverterSettings): Boolean {
        if (checkInspectionIsEnabled && !isInspectionEnabledInCurrentProfile(inspection, element.project)) return false
        return inspection.isApplicable(element) && additionalChecker(element)
    }

    override fun apply(element: E) {
        inspection.applyTo(element)
    }

    override val writeActionNeeded = writeActionNeeded
}

internal fun isInspectionEnabledInCurrentProfile(inspection: AbstractKotlinInspection, project: Project): Boolean {
    if (isUnitTestMode()) {
        // there is no real inspection profile in J2K tests, consider all inspections to be enabled in tests
        return true
    }
    val inspectionProfile = InspectionProfileManager.getInstance(project).getCurrentProfile()
    val highlightDisplayKey = HighlightDisplayKey.findById(inspection.getID())
    return inspectionProfile.isToolEnabled(highlightDisplayKey)
}