// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2.copyPaste

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaUseSiteVisibilityChecker
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.j2k.copyPaste.ConversionData
import org.jetbrains.kotlin.j2k.copyPaste.PlainTextPasteImportResolver
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression

/**
 * Tests: [org.jetbrains.kotlin.j2k.k2.K2TextJavaToKotlinCopyPasteConversionTestGenerated].
 */
internal class K2PlainTextPasteImportResolver(private val conversionData: ConversionData, private val targetKotlinFile: KtFile) :
    PlainTextPasteImportResolver {
    private val sourceJavaFile: PsiJavaFile = conversionData.sourceJavaFile
    private val javaFileImportList: PsiImportList = sourceJavaFile.importList!!
    private val project = targetKotlinFile.project
    private val scope: GlobalSearchScope = targetKotlinFile.resolveScope

    private val psiElementFactory = PsiElementFactory.getInstance(project)
    private val shortNameCache = PsiShortNamesCache.getInstance(project)

    private val failedToResolveReferenceNames: MutableSet<String> = mutableSetOf()
    private val importsToAddToKotlinFile: MutableList<PsiImportStatementBase> = mutableListOf()

    override fun generateRequiredImports(): List<PsiImportStatementBase> {
        ThreadingAssertions.assertBackgroundThread()

        if (javaFileImportList !in conversionData.elementsAndTexts.toList()) {
            addImportsToJavaFileFromKotlinFile()
            ProgressManager.checkCanceled()
        }
        tryToResolveShortReferencesByAddingImports()
        return importsToAddToKotlinFile
    }

    // TODO removing this function doesn't affect existing tests
    //  investigate is this needed or not
    private fun addImportsToJavaFileFromKotlinFile() {
        val collectedJavaImports = mutableListOf<PsiImportStatementBase>()

        runReadAction {
            val kotlinImports = targetKotlinFile.importDirectives
            kotlinImports.forEachIndexed { index, kotlinImport ->
                ProgressManager.checkCanceled()
                ProgressManager.getInstance().progressIndicator?.fraction = 1.0 * index / kotlinImports.size

                val javaImport = tryToConvertKotlinImportToJava(kotlinImport)
                if (javaImport != null) {
                    collectedJavaImports.add(javaImport)
                }
            }
        }

        runWriteActionOnEDTSync {
            for (javaImport in collectedJavaImports) {
                addImport(javaImport, shouldAddToKotlinFile = false)
            }
        }
    }

    private fun tryToConvertKotlinImportToJava(kotlinImport: KtImportDirective): PsiImportStatementBase? {
        val importPath = kotlinImport.importPath
        val importedReference = kotlinImport.importedReference
        if (importPath == null || importPath.hasAlias() || importedReference !is KtDotQualifiedExpression) return null

        val receiver = importedReference
            .receiverExpression
            .referenceExpression()
            ?.mainReference
            ?.resolve()
        val selector = importedReference
            .selectorExpression
            ?.referenceExpression()
            ?.mainReference
            ?.resolve()

        val isPackageReceiver = receiver is PsiPackage
        val isClassReceiver = receiver is PsiClass
        val isClassSelector = selector is PsiClass

        val javaImport = if (importPath.isAllUnder) {
            when {
                isClassReceiver -> psiElementFactory.createImportStaticStatement(receiver, "*")
                isPackageReceiver -> psiElementFactory.createImportStatementOnDemand(receiver.qualifiedName)
                else -> null
            }
        } else {
            when {
                isClassSelector -> psiElementFactory.createImportStatement(selector)
                isClassReceiver -> psiElementFactory.createImportStaticStatement(receiver, importPath.importedName!!.asString())
                else -> null
            }
        }

        return javaImport
    }

    private fun addImport(javaImport: PsiImportStatementBase, shouldAddToKotlinFile: Boolean) {
        javaFileImportList.add(javaImport)
        if (shouldAddToKotlinFile) importsToAddToKotlinFile.add(javaImport)
    }

    private fun tryToResolveShortReferencesByAddingImports() {
        val progressIndicator = ProgressManager.getInstance().progressIndicator
        progressIndicator?.isIndeterminate = false
        val elementPointersWithUnresolvedReferences = findUnresolvedReferencesInFile()

        for ((index, pointer) in elementPointersWithUnresolvedReferences.withIndex()) {
            progressIndicator?.fraction = 1.0 * index / elementPointersWithUnresolvedReferences.size
            val reference = runReadAction { pointer.element?.reference as? PsiQualifiedReference } ?: continue
            if (tryToResolveShortNameReference(reference)) continue
            val referenceName = runReadAction { reference.referenceName } ?: continue
            failedToResolveReferenceNames += referenceName
        }
    }

    private fun findUnresolvedReferencesInFile(): List<SmartPsiElementPointer<PsiElement>> {
        val manager = SmartPointerManager.getInstance(sourceJavaFile.project)
        return project.runReadActionInSmartMode {
            val unresolvedReferences = PsiTreeUtil.collectElements(sourceJavaFile) { element ->
                element.reference is PsiQualifiedReference && element.reference?.resolve() == null
            }
            unresolvedReferences.map { manager.createSmartPsiElementPointer(it) }.reversed()
        }
    }

    /**
     * Attempts to resolve a given unqualified (short name) reference and add the necessary import.
     * @return `true` if the reference is resolved successfully, `false` otherwise.
     */
    private fun tryToResolveShortNameReference(reference: PsiQualifiedReference): Boolean {
        ProgressManager.checkCanceled()
        if (reference.isResolved()) return true

        val referenceName = runReadAction { reference.referenceName } ?: return false
        if (referenceName in failedToResolveReferenceNames) return false
        if (runReadAction { reference.qualifier } != null) return false

        val psiClasses = findClassesByShortName(referenceName)
        ProgressManager.checkCanceled()

        // Case 1, Java class mapped to Kotlin: add import only to the Java file, because in Kotlin it will be imported by default
        val mappedClass = psiClasses.find { psiClass ->
            val fqName = runReadAction { psiClass.kotlinFqName } ?: return@find false
            JavaToKotlinClassMap.mapJavaToKotlin(fqName) != null
        }
        if (mappedClass != null) {
            runWriteActionOnEDTSync {
                val import = psiElementFactory.createImportStatement(mappedClass)
                addImport(import, shouldAddToKotlinFile = false)
            }
        }

        if (reference.isResolved()) return true

        // Case 2, Regular unique Java class: add imports both to Java and Kotlin files
        psiClasses.singleOrNull()?.let { psiClass ->
            runWriteActionOnEDTSync {
                val import = psiElementFactory.createImportStatement(psiClass)
                addImport(import, shouldAddToKotlinFile = true)
            }
        }

        if (reference.isResolved()) return true

        // Case 3, found multiple classes with the same short name: ambiguity
        if (psiClasses.isNotEmpty()) return false

        // Case 4, Regular unique Java member: add imports both to Java and Kotlin files
        val psiMember = findUniqueMemberByShortName(referenceName)
        ProgressManager.checkCanceled()
        if (psiMember != null) {
            runWriteActionOnEDTSync {
                val import = psiElementFactory.createImportStaticStatement(psiMember.containingClass!!, psiMember.name!!)
                addImport(import, shouldAddToKotlinFile = true)
            }
        }

        return reference.isResolved()
    }

    private fun PsiQualifiedReference.isResolved(): Boolean =
        runReadAction { this.resolve() } != null

    @OptIn(KaExperimentalApi::class)
    private fun findClassesByShortName(name: String): List<PsiClass> {
        return runReadAction {
            analyze(targetKotlinFile) {
                val candidateClasses = shortNameCache.getClassesByName(name, scope)
                val visibilityChecker = createUseSiteVisibilityChecker(targetKotlinFile.symbol, position = targetKotlinFile)
                candidateClasses.filter { psiClass ->
                    if (!RootKindFilter.everything.matches(psiClass.containingFile)) return@filter false
                    val declarationSymbol = if (psiClass is KtLightClass) {
                        psiClass.kotlinOrigin?.symbol
                    } else {
                        psiClass.namedClassSymbol
                    }
                    declarationSymbol != null && canBeImported(declarationSymbol, visibilityChecker)
                }
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun findUniqueMemberByShortName(name: String): PsiMember? {
        return runReadAction {
            analyze(targetKotlinFile) {
                val candidateMembers: List<PsiMember> =
                    shortNameCache.getMethodsByName(name, scope).asList() + shortNameCache.getFieldsByName(name, scope).asList()
                if (candidateMembers.isEmpty()) return@analyze null
                val visibilityChecker = createUseSiteVisibilityChecker(targetKotlinFile.symbol, position = targetKotlinFile)
                candidateMembers.filter { member ->
                    if (member.module == null) return@filter false
                    val callableSymbol = member.callableSymbol ?: return@filter false
                    canBeImported(callableSymbol, visibilityChecker)
                }.singleOrNull()
            }
        }
    }

    @OptIn(KaExperimentalApi::class, KaIdeApi::class)
    private fun KaSession.canBeImported(symbol: KaDeclarationSymbol, visibilityChecker: KaUseSiteVisibilityChecker): Boolean {
        return symbol.importableFqName != null && visibilityChecker.isVisible(symbol)
    }

    private fun runWriteActionOnEDTSync(runnable: () -> Unit) {
        ApplicationManager.getApplication().invokeAndWait { runWriteAction { runnable() } }
    }
}
