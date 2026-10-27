// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.mcp.imports

import com.intellij.lang.LanguageImportStatements
import com.intellij.mcpserver.imports.McpImportCandidate
import com.intellij.mcpserver.imports.McpImportChange
import com.intellij.mcpserver.imports.McpMissingImport
import com.intellij.mcpserver.imports.McpMissingImportProvider
import com.intellij.modcommand.ModCommand
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.codeInsight.KotlinReferenceImporterFacility
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportLikeQuickFix
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

/**
 * Adds a missing Kotlin import.
 */
internal class KotlinMissingImportProvider : McpMissingImportProvider {

    override fun addMissingImports(file: PsiFile, takeBestCandidate: Boolean, optimize: Boolean): McpImportChange? {
        if (file !is KtFile) return null
        val foundNames = LinkedHashMap<String, FoundName>()
        val remainingNames = HashSet<String>()
        val command = ModCommand.psiUpdate(file) { copy ->
            importAll(copy, takeBestCandidate, foundNames)
            if (optimize) optimizeImports(copy)
            unresolvedExpressions(copy).mapTo(remainingNames) { it.getReferencedName() }
        }

        val missingImports = foundNames.map { (name, found) -> McpMissingImport(name, found.offset, found.candidates) }
        return McpImportChange(missingImports, command, remainingNames)
    }
}

private class FoundName(val offset: Int, val candidates: List<McpImportCandidate>)

private fun importAll(file: KtFile, takeBestCandidate: Boolean, foundNames: MutableMap<String, FoundName>) {
    val facility = KotlinReferenceImporterFacility.getInstance()
    val pointerManager = SmartPointerManager.getInstance(file.project)
    // A pass that adds no import ends the loop, so a name that no import can fix stops it too.
    do {
        val importsBefore = importsOf(file)
        // The offsets are taken before the pass changes anything, so the first pass keeps the offsets of the file.
        val pending = unresolvedExpressions(file).map { pointerManager.createSmartPsiElementPointer(it) to it.textRange.startOffset }
        val undecidedNames = HashSet<String>()
        for ((pointer, offset) in pending) {
            val expression = pointer.element ?: continue
            if (expression.mainReference.resolve() != null) continue
            val name = expression.getReferencedName()
            if (name in undecidedNames) continue
            val variants = importVariantsOf(facility, expression, file)
            foundNames.putIfAbsent(name, FoundName(offset, variants.map { it.toCandidate() }))
            if (variants.isEmpty() || variants.size > 1 && !takeBestCandidate) {
                undecidedNames.add(name)
                continue
            }
            file.addImport(variants.first().fqName)
        }
    } while (importsOf(file) != importsBefore)
}

private fun optimizeImports(file: PsiFile) {
    for (optimizer in LanguageImportStatements.INSTANCE.forFile(file)) {
        optimizer.processFile(file).run()
    }
}

private fun importsOf(file: KtFile): List<String> = file.importDirectives.mapNotNull { it.importPath?.pathStr }

private fun unresolvedExpressions(file: KtFile): List<KtSimpleNameExpression> =
    SyntaxTraverser.psiTraverser(file)
        .filter(KtSimpleNameExpression::class.java)
        .filter { it.mainReference.resolvesByNames.isNotEmpty() && canTakeImport(it) && it.mainReference.resolve() == null }
        .toList()

private fun importVariantsOf(
    facility: KotlinReferenceImporterFacility,
    expression: KtSimpleNameExpression,
    file: KtFile,
): List<AutoImportVariant> {
    return facility.createImportFixesForExpression(expression)
        .filterIsInstance<ImportLikeQuickFix>()
        .filter { it.isAvailable(file.project, file) }
        .map { it.importVariants }
        .firstOrNull { it.isNotEmpty() }
        ?: emptyList()
}

private fun canTakeImport(expression: KtSimpleNameExpression): Boolean {
    return expression.parentOfType<KtImportDirective>(withSelf = true) == null &&
            expression.parentOfType<KtPackageDirective>(withSelf = true) == null
}

private fun AutoImportVariant.toCandidate(): McpImportCandidate =
    McpImportCandidate(fqName.asString(), declarationToImport?.let { sourceOf(it) }, deprecated)

/** Returns the module or the library that holds [element], or null when neither is known. */
private fun sourceOf(element: PsiElement): String? {
    ModuleUtilCore.findModuleForPsiElement(element)?.let { return it.name }

    val virtualFile = PsiUtilCore.getVirtualFile(element) ?: return null
    return ProjectFileIndex.getInstance(element.project).getOrderEntriesForFile(virtualFile).firstOrNull()?.presentableName
}
