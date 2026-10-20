// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.diagnostics.diagnostics
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.test.Diagnostic
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration

private val markerPattern = Regex("// (DECL|REF): ([12])")

@OptIn(KaAllowAnalysisOnEdt::class)
fun checkMarkedRenameResult(file: KtFile) {
    DirectiveBasedActionUtils.checkForUnexpectedErrors(file, diagnosticsProvider = { ktFile ->
        allowAnalysisOnEdt {
            analyze(ktFile) {
                ktFile.diagnostics().map { diagnostic ->
                    val severity = when (diagnostic.severity) {
                        KaSeverity.ERROR -> Severity.ERROR
                        KaSeverity.WARNING -> Severity.WARNING
                        KaSeverity.INFO -> Severity.INFO
                    }
                    Diagnostic(diagnostic.factoryName, severity)
                }.toList()
            }
        }
    })

    val declarations = PsiTreeUtil.collectElementsOfType(file, KtNamedDeclaration::class.java)
    val references = PsiTreeUtil.collectElementsOfType(file, KtNameReferenceExpression::class.java)
    val markedDeclarations = mutableMapOf<String, KtNamedDeclaration>()
    val markedReferences = mutableMapOf<String, PsiComment>()
    for (comment in PsiTreeUtil.collectElementsOfType(file, PsiComment::class.java)) {
        val match = markerPattern.matchEntire(comment.text) ?: continue
        val id = match.groupValues[2]
        val lineStart = file.text.lastIndexOf('\n', comment.textOffset - 1) + 1
        if (match.groupValues[1] == "DECL") {
            val onLine = declarations.filter { declaration ->
                declaration.nameIdentifier?.textOffset?.let { it in lineStart until comment.textOffset } == true
            }
            assertEquals("Expected one declaration for marker $id", 1, onLine.size)
            assertTrue("Duplicate declaration marker $id", markedDeclarations.put(id, onLine.single()) == null)
        }
        else {
            assertTrue("Duplicate reference marker $id", markedReferences.put(id, comment) == null)
        }
    }

    assertEquals(setOf("1", "2"), markedDeclarations.keys)
    assertEquals(markedDeclarations.keys, markedReferences.keys)
    for ((id, comment) in markedReferences) {
        val declaration = markedDeclarations.getValue(id)
        val lineStart = file.text.lastIndexOf('\n', comment.textOffset - 1) + 1
        val onLine = references.filter {
            it.textOffset in lineStart until comment.textOffset && it.getReferencedName() == declaration.name
        }.distinctBy { it.textRange }
        assertEquals("Expected one reference for marker $id", 1, onLine.size)
        val target = onLine.single().mainReference.resolve()?.navigationElement
        assertTrue("Reference $id resolves to $target instead of $declaration", file.manager.areElementsEquivalent(declaration, target))
    }
}
