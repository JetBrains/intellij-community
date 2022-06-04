// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.postProcessing.processings

import com.intellij.openapi.editor.RangeMarker
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.idea.core.util.range
import org.jetbrains.kotlin.idea.imports.KotlinImportOptimizer
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.j2k.JKPostProcessingTarget
import org.jetbrains.kotlin.j2k.elements
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.asLabel
import org.jetbrains.kotlin.nj2k.postProcessing.FileBasedPostProcessing
import org.jetbrains.kotlin.nj2k.postProcessing.GeneralPostProcessing
import org.jetbrains.kotlin.nj2k.postProcessing.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange


class FormatCodeProcessing : FileBasedPostProcessing() {
    override fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: NewJ2kConverterContext) {
        val codeStyleManager = CodeStyleManager.getInstance(file.project)
        runUndoTransparentActionInEdt(inWriteAction = true) {
            if (rangeMarker != null) {
                if (rangeMarker.isValid) {
                    codeStyleManager.reformatRange(file, rangeMarker.startOffset, rangeMarker.endOffset)
                }
            } else {
                codeStyleManager.reformat(file)
            }
        }
    }
}


class ClearUnknownLabelsProcessing : GeneralPostProcessing {
    override fun runProcessing(target: JKPostProcessingTarget, converterContext: NewJ2kConverterContext) {
        val comments = mutableListOf<PsiComment>()
        runUndoTransparentActionInEdt(inWriteAction = true) {
            target.elements().forEach { element ->
                element.accept(object : PsiElementVisitor() {
                    override fun visitElement(element: PsiElement) {
                        element.acceptChildren(this)
                    }

                    override fun visitComment(comment: PsiComment) {
                        if (comment.text.asLabel() != null) {
                            comments += comment
                        }
                    }
                })
            }
            comments.forEach { it.delete() }
        }
    }
}


class OptimizeImportsProcessing : FileBasedPostProcessing() {
    override fun runProcessing(file: KtFile, allFiles: List<KtFile>, rangeMarker: RangeMarker?, converterContext: NewJ2kConverterContext) {
        val elements = runReadAction {
            when {
                rangeMarker != null && rangeMarker.isValid -> file.elementsInRange(rangeMarker.range!!)
                rangeMarker != null && !rangeMarker.isValid -> emptyList()
                else -> file.children.asList()
            }
        }
        val shouldOptimize = elements.any { element ->
            element is KtElement
                    && element !is KtImportDirective
                    && element !is KtImportList
                    && element !is KtPackageDirective
        }
        if (shouldOptimize) {
            val importsReplacer = runReadAction { KotlinImportOptimizer().processFile(file) }
            runUndoTransparentActionInEdt(inWriteAction = true) {
                importsReplacer.run()
            }
        }
    }
}
