// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.postProcessings

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.asTextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.findClassLike
import org.jetbrains.kotlin.analysis.api.symbols.findTopLevelCallables
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.imports.KotlinImportPathComparator
import org.jetbrains.kotlin.idea.imports.ImportMapper
import org.jetbrains.kotlin.idea.imports.KotlinIdeDefaultImportProvider
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.FileBasedPostProcessing
import org.jetbrains.kotlin.j2k.PostProcessingApplier
import org.jetbrains.kotlin.j2k.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * NOTE: This class is J2K-specific, do not confuse it with [com.intellij.codeInsight.actions.OptimizeImportsProcessor].
 *
 * Removes the unused imports and the imports that a Kotlin alias in the default imports replaces, such as `java.util.ArrayList`.
 * Sorts the other imports by the code style. It does not replace imports with star imports,
 * so it needs no check for changed references.
 */
class OptimizeImportsProcessing : FileBasedPostProcessing() {
    private fun shouldTryToOptimizeImports(file: KtFile, rangeMarker: RangeMarker?): Boolean {
        val elements = runReadAction {
            when {
                rangeMarker != null && rangeMarker.isValid -> file.elementsInRange(rangeMarker.asTextRange!!)
                rangeMarker != null && !rangeMarker.isValid -> emptyList()
                else -> file.children.asList()
            }
        }
        return elements.any {
            it is KtElement
                    && it !is KtImportDirective
                    && it !is KtImportList
                    && it !is KtPackageDirective
        }
    }

    override fun computeApplier(
        file: KtFile,
        allFiles: List<KtFile>,
        rangeMarker: RangeMarker?,
        converterContext: ConverterContext
    ): PostProcessingApplier {
        if (!shouldTryToOptimizeImports(file, rangeMarker)) return Applier.EMPTY
        // An import without a path cannot be written back, so the file keeps its imports as they are
        if (file.importDirectives.any { it.importPath == null }) return Applier.EMPTY
        val oldImports = file.importDirectives.mapNotNull { it.importPath }
        val importData = KotlinOptimizeImportsFacility.getInstance().analyzeImports(file) ?: return Applier.EMPTY
        val unusedImports = importData.unusedImports.toSet()
        val usedImports = file.importDirectives.filter { it !in unusedImports }.mapNotNull { it.importPath }
        val aliasedImports = findImportsReplacedByDefaultAliases(file, usedImports)
        val newImports = (usedImports - aliasedImports).sortedWith(KotlinImportPathComparator.create(file))
        if (newImports == oldImports) return Applier.EMPTY
        return Applier(file.createSmartPointer(), newImports)
    }

    /**
     * Finds the imports such as `java.util.ArrayList` that a default import replaces with a Kotlin alias of the same class.
     * An import stays when its name qualifies a nested class, or when the package of the file or a star import declares the same name.
     */
    private fun findImportsReplacedByDefaultAliases(file: KtFile, imports: List<ImportPath>): Set<ImportPath> {
        val apiVersion = file.languageVersionSettings.apiVersion
        val defaultImportProvider = KotlinIdeDefaultImportProvider.getInstance()
        val candidates = imports.filter { importPath ->
            if (importPath.isAllUnder || importPath.hasAlias()) return@filter false
            val kotlinFqName = ImportMapper.findCorrespondingKotlinFqName(importPath.fqName, apiVersion) ?: return@filter false
            defaultImportProvider.isImportedWithDefault(ImportPath(kotlinFqName, isAllUnder = false), file)
        }
        if (candidates.isEmpty()) return emptySet()

        val candidateNames = candidates.mapTo(hashSetOf()) { it.fqName.shortName() }
        val qualifiers = file.collectDescendantsOfType<KtNameReferenceExpression> { it.getReferencedNameAsName() in candidateNames }
        val packages = listOf(file.packageFqName) + file.importDirectives.filter { it.isAllUnder }.mapNotNull { it.importedFqName }
        return analyze(file) {
            val classQualifierNames = qualifiers.filter { qualifiesClassifier(it) }.mapTo(hashSetOf()) { it.getReferencedNameAsName() }
            candidates.filterTo(hashSetOf()) { importPath ->
                val name = importPath.fqName.shortName()
                name !in classQualifierNames && packages.none { packageFqName ->
                    findClassLike(ClassId(packageFqName, name)) != null || findTopLevelCallables(packageFqName, name).any()
                }
            }
        }
    }

    // A Kotlin alias gives the static members of a Java class, but not its nested classes
    context(_: KaSession)
    private fun qualifiesClassifier(reference: KtNameReferenceExpression): Boolean {
        val parent = reference.parent
        if (parent is KtUserType) return (parent.parent as? KtUserType)?.qualifier == parent
        if (parent !is KtDotQualifiedExpression || parent.receiverExpression != reference) return false
        val selector = parent.selectorExpression
        val callee = (selector as? KtCallExpression)?.calleeExpression ?: selector
        val symbol = (callee as? KtReferenceExpression)?.mainReference?.resolveToSymbol()
        return symbol !is KaCallableSymbol || symbol is KaConstructorSymbol
    }

    private class Applier(private val filePointer: SmartPsiElementPointer<KtFile>?, private val imports: List<ImportPath>) : PostProcessingApplier {
        override fun apply() {
            val file = filePointer?.element ?: return
            KotlinOptimizeImportsFacility.getInstance().replaceImports(file, imports)
        }

        companion object {
            val EMPTY = Applier(null, emptyList())
        }
    }
}
