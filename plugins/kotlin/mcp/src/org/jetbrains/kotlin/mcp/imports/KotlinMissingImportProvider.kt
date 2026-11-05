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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticCheckerKind
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.diagnostics.diagnostics
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixService
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportLikeQuickFix
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.analysisContext
import org.jetbrains.kotlin.util.OperatorNameConventions

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
            missingNamesOf(snapshotOf(copy)).mapTo(remainingNames) { it.name }
        }

        val missingImports = foundNames.map { (name, found) -> McpMissingImport(name, found.offset, found.candidates) }
        return McpImportChange(missingImports, command, remainingNames)
    }
}

private class FoundName(val offset: Int, val candidates: List<McpImportCandidate>)

/** An error that an import can fix, with the imports that fix it. [variants] is empty when no import does. */
private class MissingName(val name: String, val offset: Int, val variants: List<AutoImportVariant>)

private fun importAll(file: KtFile, takeBestCandidate: Boolean, foundNames: MutableMap<String, FoundName>) {
    // A pass that adds no import ends the loop, so a name that no import can fix stops it too.
    do {
        val importsBefore = importsOf(file)
        // The offsets are taken before the pass changes anything, so the first pass keeps the offsets of the file.
        val missingNames = missingNamesOf(snapshotOf(file))
        for (missing in missingNames) {
            val found = FoundName(missing.offset, missing.variants.map { it.toCandidate() })
            // An error of an earlier pass can have no candidate yet, because the type of its receiver was not imported.
            foundNames.merge(missing.name, found) { old, new -> if (old.candidates.isEmpty()) FoundName(old.offset, new.candidates) else old }
        }
        // A function waits for the classes: an operator imported before the class of another receiver turns the error
        // there from NO_GET_METHOD into NONE_APPLICABLE, and no import fix handles NONE_APPLICABLE on an array access.
        val (classNames, otherNames) = missingNames.partition {
            val declaration = it.variants.firstOrNull()?.declarationToImport
            declaration is KtClassLikeDeclaration || declaration is PsiClass
        }
        if (!importDecided(file, classNames, takeBestCandidate)) {
            importDecided(file, otherNames, takeBestCandidate)
        }
    } while (importsOf(file) != importsBefore)
}

/** Imports every name that has one candidate, or the first one with [takeBestCandidate]. Returns true when it added any. */
private fun importDecided(file: KtFile, missingNames: List<MissingName>, takeBestCandidate: Boolean): Boolean {
    val undecidedNames = HashSet<String>()
    var imported = false
    for (missing in missingNames) {
        if (missing.name in undecidedNames) continue
        if (missing.variants.isEmpty() || missing.variants.size > 1 && !takeBestCandidate) {
            undecidedNames.add(missing.name)
            continue
        }
        file.addImport(missing.variants.first().fqName)
        imported = true
    }
    return imported
}

private fun optimizeImports(file: PsiFile) {
    for (optimizer in LanguageImportStatements.INSTANCE.forFile(file)) {
        optimizer.processFile(file).run()
    }
}

private fun importsOf(file: KtFile): List<String> = file.importDirectives.mapNotNull { it.importPath?.pathStr }

/**
 * Returns a new copy of [file] to analyze one pass in.
 *
 * An import fix caches its own copy of the analyzed file for the whole read action. The tool adds the imports of all
 * passes in one read action, so a fix on [file] itself would compare against the file of the first pass.
 */
private fun snapshotOf(file: KtFile): KtFile {
    // An added import gets its line break from the postponed formatting; without it the text glues two imports into one.
    PostprocessReformattingAspect.getInstance(file.project).doPostponedFormatting(file.viewProvider)
    return KtPsiFactory(file.project).createFile(file.name, file.text).apply {
        analysisContext = file.analysisContext ?: file.originalFile.takeIf { it !== file }
    }
}

private fun missingNamesOf(file: KtFile): List<MissingName> = analyze(file) {
    val quickFixService = KotlinQuickFixService.getInstance()
    val diagnostics = file.diagnostics()
        .withCheckers(KaDiagnosticCheckerKind.COMMON, KaDiagnosticCheckerKind.EXTENDED)
        .filter { it.severity == KaSeverity.ERROR && canTakeImport(it.psi) }

    val missingNames = ArrayList<MissingName>()
    for (diagnostic in diagnostics) {
        val variants = with(quickFixService) { getLazyQuickFixesFor(diagnostic) }
            .filterIsInstance<ImportLikeQuickFix>()
            .filter { it.isAvailable(file.project, file) }
            .map { it.importVariants }
            .firstOrNull { it.isNotEmpty() }
            ?: emptyList()
        val operatorName = missingOperatorName(diagnostic)
        val isUnresolvedName = diagnostic is KaFirDiagnostic.UnresolvedReference || diagnostic is KaFirDiagnostic.UnresolvedReferenceWrongReceiver
        if (variants.isEmpty() && !isUnresolvedName && operatorName == null) continue

        val nameExpression = diagnostic.psi as? KtSimpleNameExpression ?: (diagnostic.psi as? KtBinaryExpression)?.operationReference
        val name = operatorName ?: nameExpression?.getReferencedName()
                   ?: variants.firstOrNull()?.fqName?.shortName()?.asString() ?: diagnostic.psi.text
        missingNames.add(MissingName(name, diagnostic.psi.textRange.startOffset, variants))
    }
    missingNames
}

/** The operator function that the error says is missing, or null when the error is not about an operator. */
private fun missingOperatorName(diagnostic: KaDiagnosticWithPsi<*>): String? = when (diagnostic) {
    is KaFirDiagnostic.NoGetMethod -> OperatorNameConventions.GET.asString()
    is KaFirDiagnostic.NoSetMethod -> OperatorNameConventions.SET.asString()
    is KaFirDiagnostic.ComponentFunctionMissing -> diagnostic.missingFunctionName.asString()
    is KaFirDiagnostic.IteratorMissing -> OperatorNameConventions.ITERATOR.asString()
    is KaFirDiagnostic.HasNextMissing -> OperatorNameConventions.HAS_NEXT.asString()
    is KaFirDiagnostic.NextMissing -> OperatorNameConventions.NEXT.asString()
    is KaFirDiagnostic.DelegateSpecialFunctionMissing -> diagnostic.expectedFunctionSignature.substringBefore('(')
    else -> null
}

private fun canTakeImport(element: PsiElement): Boolean {
    return element.parentOfType<KtImportDirective>(withSelf = true) == null &&
            element.parentOfType<KtPackageDirective>(withSelf = true) == null
}

private fun AutoImportVariant.toCandidate(): McpImportCandidate =
    McpImportCandidate(fqName.asString(), declarationToImport?.let { sourceOf(it) }, deprecated)

/** Returns the module or the library that holds [element], or null when neither is known. */
private fun sourceOf(element: PsiElement): String? {
    ModuleUtilCore.findModuleForPsiElement(element)?.let { return it.name }

    val virtualFile = PsiUtilCore.getVirtualFile(element) ?: return null
    return ProjectFileIndex.getInstance(element.project).getOrderEntriesForFile(virtualFile).firstOrNull()?.presentableName
}
