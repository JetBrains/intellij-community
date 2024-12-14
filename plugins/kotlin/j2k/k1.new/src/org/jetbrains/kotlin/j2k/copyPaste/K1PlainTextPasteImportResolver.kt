// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaMemberDescriptor
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.idea.imports.canBeReferencedViaImport
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression

/**
 * Tests: [org.jetbrains.kotlin.nj2k.K1JavaToKotlinCopyPasteConversionTestGenerated].
 */
class K1PlainTextPasteImportResolver(private val conversionData: ConversionData, private val targetKotlinFile: KtFile) :
    PlainTextPasteImportResolver {
    private val sourceJavaFile: PsiJavaFile = conversionData.sourceJavaFile
    private val javaFileImportList: PsiImportList = sourceJavaFile.importList!!
    private val project = targetKotlinFile.project
    private val scope: GlobalSearchScope = targetKotlinFile.resolveScope

    private val psiElementFactory = PsiElementFactory.getInstance(project)
    private val shortNameCache = PsiShortNamesCache.getInstance(project)
    private val bindingContext by lazy { targetKotlinFile.analyzeWithContent() }
    private val resolutionFacade = targetKotlinFile.getResolutionFacade()

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

    private fun findClassesByShortName(name: String): List<PsiClass> {
        return runReadAction {
            val candidateClasses = shortNameCache.getClassesByName(name, scope)
            candidateClasses.filter { psiClass ->
                if (!RootKindFilter.everything.matches(psiClass.containingFile)) return@filter false
                val descriptor = psiClass.getJavaMemberDescriptor() as? ClassDescriptor ?: return@filter false
                canBeImported(descriptor)
            }
        }
    }

    @OptIn(K1ModeProjectStructureApi::class)
    private fun findUniqueMemberByShortName(name: String): PsiMember? {
        return runReadAction {
            val candidateMembers: List<PsiMember> =
                shortNameCache.getMethodsByName(name, scope).asList() + shortNameCache.getFieldsByName(name, scope).asList()
            candidateMembers.filter { member ->
                if (member.moduleInfoOrNull == null) return@filter false
                val descriptor = member.getJavaMemberDescriptor(resolutionFacade) as? DeclarationDescriptorWithVisibility
                    ?: return@filter false
                canBeImported(descriptor)
            }.singleOrNull()
        }
    }

    private fun canBeImported(descriptor: DeclarationDescriptorWithVisibility): Boolean {
        return descriptor.canBeReferencedViaImport() && descriptor.isVisible(targetKotlinFile, null, bindingContext, resolutionFacade)
    }

    private fun runWriteActionOnEDTSync(runnable: () -> Unit) {
        ApplicationManager.getApplication().invokeAndWait { runWriteAction { runnable() } }
    }
}