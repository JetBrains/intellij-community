// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.postProcessings

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.components.QualifierToShortenInfo
import org.jetbrains.kotlin.analysis.api.components.TypeToShortenInfo
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.idea.base.analysis.api.utils.ShortenCommandForIde
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectPossibleReferenceShorteningsForIde
import org.jetbrains.kotlin.idea.base.analysis.api.utils.invokeShortening
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenQualifier
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.FileBasedPostProcessing
import org.jetbrains.kotlin.j2k.PostProcessingApplier
import org.jetbrains.kotlin.j2k.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

// TODO is it necessary to use `JKImportStorage.isImportNeededForCall`, like in K1?
internal class K2ShortenReferenceProcessing : FileBasedPostProcessing() {
    override fun computeApplier(
        file: KtFile,
        allFiles: List<KtFile>,
        rangeMarker: RangeMarker?,
        converterContext: ConverterContext
    ): PostProcessingApplier {
        val range = if (rangeMarker != null && rangeMarker.isValid) rangeMarker.textRange else file.textRange
        val shortenCommand = analyze(file) { collectPossibleReferenceShorteningsForIde(file, range) }
        return Applier(shortenCommand, file.project)
    }

    private class Applier(private val shortenCommand: ShortenCommandForIde, private val project: Project) : PostProcessingApplier {
        override fun apply() {
            CodeStyleManager.getInstance(project).performActionWithFormatterDisabled {
                shortenTypesAndQualifiersFromTheEnd()
                shortenCommand.withoutTypesAndQualifiers().invokeShortening(results = null)
            }
        }

        /**
         * Shortens the types and the qualifiers with direct references instead of smart pointers.
         * Each PSI edit updates every live smart pointer of the file, so the pointers of a large file make the edits quadratic.
         * The edits go from the end of the file to its start: an element inside another one starts later, so it is shortened first,
         * and an edit never touches an element that starts before it.
         */
        private fun shortenTypesAndQualifiersFromTheEnd() {
            if (shortenCommand.targetFile.element == null) return
            val psiFactory = KtPsiFactory(project)
            val pointerManager = SmartPointerManager.getInstance(project)

            class Edit(val range: TextRange, val apply: () -> Unit)

            val edits = ArrayList<Edit>(shortenCommand.listOfTypeToShortenInfo.size + shortenCommand.listOfQualifierToShortenInfo.size)
            for ((typePointer, shortenedRef) in shortenCommand.listOfTypeToShortenInfo) {
                val type = typePointer.element ?: continue
                pointerManager.removePointer(typePointer)
                edits += Edit(type.textRange) { if (type.isValid) type.shortenType(shortenedRef, psiFactory) }
            }
            for ((qualifierPointer, shortenedReference) in shortenCommand.listOfQualifierToShortenInfo) {
                val call = qualifierPointer.element ?: continue
                pointerManager.removePointer(qualifierPointer)
                edits += Edit(call.textRange) { if (call.isValid) call.shortenQualifier(shortenedReference, psiFactory) }
            }
            edits.sortWith(compareByDescending<Edit> { it.range.startOffset }.thenBy { it.range.endOffset })
            for (edit in edits) {
                edit.apply()
            }
        }

        @OptIn(KaImplementationDetail::class)
        private fun ShortenCommandForIde.withoutTypesAndQualifiers(): ShortenCommandForIde = object : ShortenCommandForIde by this {
            override val listOfTypeToShortenInfo: List<TypeToShortenInfo> get() = emptyList()
            override val listOfQualifierToShortenInfo: List<QualifierToShortenInfo> get() = emptyList()
        }
    }
}