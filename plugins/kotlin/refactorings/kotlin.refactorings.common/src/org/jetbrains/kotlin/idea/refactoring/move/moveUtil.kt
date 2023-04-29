// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.ide.util.DirectoryUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.*
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.move.moveMembers.MoveMemberHandler
import com.intellij.refactoring.move.moveMembers.MoveMembersOptions
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.NonCodeUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import com.intellij.util.SmartList
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.getPackage
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import java.io.File
import java.util.*

var KtFile.allElementsToMove: List<PsiElement>? by UserDataProperty(Key.create("SCOPE_TO_MOVE"))

var KtSimpleNameExpression.internalUsageInfo: UsageInfo? by CopyablePsiUserDataProperty(Key.create("INTERNAL_USAGE_INFO"))

var KtFile.updatePackageDirective: Boolean? by UserDataProperty(Key.create("UPDATE_PACKAGE_DIRECTIVE"))

fun getTargetPackageFqName(targetContainer: PsiElement): FqName? {
    if (targetContainer is PsiDirectory) {
        val targetPackage = targetContainer.getPackage()
        return if (targetPackage != null) FqName(targetPackage.qualifiedName) else null
    }
    return if (targetContainer is KtFile) targetContainer.packageFqName else null
}

fun markInternalUsages(usages: Collection<UsageInfo>) {
    usages.forEach { (it.element as? KtSimpleNameExpression)?.internalUsageInfo = it }
}

fun restoreInternalUsages(
    scope: KtElement,
    oldToNewElementsMapping: Map<PsiElement, PsiElement>,
    forcedRestore: Boolean = false
): List<UsageInfo> {
    return scope.collectDescendantsOfType<KtSimpleNameExpression>().mapNotNull {
        val usageInfo = it.internalUsageInfo
        if (!forcedRestore && usageInfo?.element != null) return@mapNotNull usageInfo
        val referencedElement = (usageInfo as? MoveRenameUsageInfo)?.referencedElement ?: return@mapNotNull null
        val newReferencedElement = mapToNewOrThis(referencedElement, oldToNewElementsMapping)
        if (!newReferencedElement.isValid) return@mapNotNull null
        (usageInfo as? KotlinMoveUsage)?.refresh(it, newReferencedElement)
    }
}

fun cleanUpInternalUsages(usages: Collection<UsageInfo>) {
    usages.forEach { (it.element as? KtSimpleNameExpression)?.internalUsageInfo = null }
}

fun guessNewFileName(declarationsToMove: Collection<KtNamedDeclaration>): String? {
    if (declarationsToMove.isEmpty()) return null
    val representative = declarationsToMove.singleOrNull()
        ?: declarationsToMove.filterIsInstance<KtClassOrObject>().singleOrNull()
    val newFileName = representative?.run {
        if (containingKtFile.isScript()) "$name.kts" else "$name.${KotlinFileType.EXTENSION}"
    } ?: declarationsToMove.first().containingFile.name
    return newFileName.capitalizeAsciiOnly()
}

// returns true if successful
private fun updateJavaReference(reference: PsiReferenceExpression, oldElement: PsiElement, newElement: PsiElement): Boolean {
    if (oldElement is PsiMember && newElement is PsiMember) {
        // Remove import of old package facade, if any
        val oldClassName = oldElement.containingClass?.qualifiedName
        if (oldClassName != null) {
            val importOfOldClass = (reference.containingFile as? PsiJavaFile)?.importList?.allImportStatements?.firstOrNull {
                when (it) {
                    is PsiImportStatement -> it.qualifiedName == oldClassName
                    is PsiImportStaticStatement -> it.isOnDemand && it.importReference?.canonicalText == oldClassName
                    else -> false
                }
            }
            if (importOfOldClass != null && importOfOldClass.resolve() == null) {
                importOfOldClass.delete()
            }
        }

        val newClass = newElement.containingClass
        if (newClass != null && reference.qualifierExpression != null) {

            val refactoringOptions = object : MoveMembersOptions {
                override fun getMemberVisibility(): String = PsiModifier.PUBLIC
                override fun makeEnumConstant(): Boolean = true
                override fun getSelectedMembers(): Array<PsiMember> = arrayOf(newElement)
                override fun getTargetClassName(): String? = newClass.qualifiedName
            }

            val moveMembersUsageInfo = MoveMembersProcessor.MoveMembersUsageInfo(
                newElement, reference.element, newClass, reference.qualifierExpression, reference
            )

            val moveMemberHandler = MoveMemberHandler.EP_NAME.forLanguage(reference.element.language)
            if (moveMemberHandler != null) {
                moveMemberHandler.changeExternalUsage(refactoringOptions, moveMembersUsageInfo)
                return true
            }
        }
    }
    return false
}

internal fun mapToNewOrThis(e: PsiElement, oldToNewElementsMapping: Map<PsiElement, PsiElement>) = oldToNewElementsMapping[e] ?: e

private fun postProcessMoveUsage(
    usage: UsageInfo,
    oldToNewElementsMapping: Map<PsiElement, PsiElement>,
    nonCodeUsages: ArrayList<NonCodeUsageInfo>,
    shorteningMode: KtSimpleNameReference.ShorteningMode
) {
    if (usage is NonCodeUsageInfo) {
        nonCodeUsages.add(usage)
        return
    }

    if (usage !is MoveRenameUsageInfo) return

    val oldElement = usage.referencedElement!!
    val newElement = mapToNewOrThis(oldElement, oldToNewElementsMapping)

    when (usage) {
        is KotlinMoveUsage.Deferred -> {
            val newUsage = usage.resolve(newElement) ?: return
            postProcessMoveUsage(newUsage, oldToNewElementsMapping, nonCodeUsages, shorteningMode)
        }

        is KotlinMoveUsage.Unqualifiable -> {
            val file = with(usage) {
                if (addImportToOriginalFile) originalFile else mapToNewOrThis(
                    originalFile,
                    oldToNewElementsMapping
                )
            } as KtFile
            KotlinMoveRefactoringSupport.getInstance().addDelayedImportRequest(newElement, file)
        }

        else -> {
            val reference = (usage.element as? KtSimpleNameExpression)?.mainReference ?: usage.reference
            processReference(reference, newElement, shorteningMode, oldElement)
        }
    }
}

private fun processReference(
    reference: PsiReference?,
    newElement: PsiElement,
    shorteningMode: KtSimpleNameReference.ShorteningMode,
    oldElement: PsiElement
) {
    try {
        when {
            reference is KtSimpleNameReference -> reference.bindToElement(newElement, shorteningMode)
            reference is PsiReferenceExpression && updateJavaReference(reference, oldElement, newElement) -> return
            else -> reference?.bindToElement(newElement)
        }
    } catch (e: IncorrectOperationException) {
        // Suppress exception if bindToElement is not implemented
    }
}

/**
 * Perform usage postprocessing and return non-code usages
 */
fun postProcessMoveUsages(
    usages: Collection<UsageInfo>,
    oldToNewElementsMapping: Map<PsiElement, PsiElement> = Collections.emptyMap(),
    shorteningMode: KtSimpleNameReference.ShorteningMode = KtSimpleNameReference.ShorteningMode.DELAYED_SHORTENING
): List<NonCodeUsageInfo> {
    val sortedUsages = usages.sortedWith(
        Comparator { o1, o2 ->
            val file1 = o1.virtualFile
            val file2 = o2.virtualFile
            if (Comparing.equal(file1, file2)) {
                val rangeInElement1 = o1.rangeInElement
                val rangeInElement2 = o2.rangeInElement
                if (rangeInElement1 != null && rangeInElement2 != null) {
                    return@Comparator rangeInElement2.startOffset - rangeInElement1.startOffset
                }
                return@Comparator 0
            }
            if (file1 == null) return@Comparator -1
            if (file2 == null) return@Comparator 1
            Comparing.compare(file1.path, file2.path)
        }
    )

    val nonCodeUsages = ArrayList<NonCodeUsageInfo>()

    val progressStep = 1.0 / sortedUsages.size
    val progressIndicator = ProgressManager.getInstance().progressIndicator
    progressIndicator?.isIndeterminate = false
    progressIndicator?.text = KotlinBundle.message("text.updating.usages.progress")
    usageLoop@ for ((i, usage) in sortedUsages.withIndex()) {
        progressIndicator?.fraction = (i + 1) * progressStep
        postProcessMoveUsage(usage, oldToNewElementsMapping, nonCodeUsages, shorteningMode)
    }
    progressIndicator?.text = ""

    return nonCodeUsages
}

fun collectOuterInstanceReferences(member: KtNamedDeclaration): List<OuterInstanceReferenceUsageInfo> {
    val result = SmartList<OuterInstanceReferenceUsageInfo>()
    KotlinMoveRefactoringSupport.getInstance().traverseOuterInstanceReferences(member, false) { result += it }
    return result
}

@Throws(IncorrectOperationException::class)
fun getOrCreateDirectory(path: String, project: Project): PsiDirectory {

    File(path).toPsiDirectory(project)?.let { return it }

    return project.executeCommand(RefactoringBundle.message("move.title"), null) {
        runWriteAction {
            val fixUpSeparators = path.replace(File.separatorChar, '/')
            DirectoryUtil.mkdirs(PsiManager.getInstance(project), fixUpSeparators)
        }
    }
}

fun <T> List<KtNamedDeclaration>.mapWithReadActionInProcess(
    project: Project,
    @NlsContexts.DialogTitle title: String,
    body: (KtNamedDeclaration) -> T
): List<T> = let { declarations ->
    val result = mutableListOf<T>()
    val task = object : Task.Modal(project, title, false) {
        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = false
            indicator.fraction = 0.0
            val fraction = 1.0 / declarations.size
            runReadAction {
                declarations.forEachIndexed { index, declaration ->
                    result.add(body(declaration))
                    indicator.fraction = fraction * index
                }
            }
        }
    }
    ProgressManager.getInstance().run(task)
    return result
}
