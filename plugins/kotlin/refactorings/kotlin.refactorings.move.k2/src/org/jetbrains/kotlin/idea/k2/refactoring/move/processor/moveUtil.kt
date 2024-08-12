// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.java.analysis.JavaAnalysisBundle
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.siblings
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor
import com.intellij.util.takeWhileInclusive
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefixOrRoot
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2ChangePackageDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.kdoc.psi.api.KDocElement
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * @return whether an [PsiElement] should be moved when it's in between moved declarations.
 */
private fun PsiElement.isContextElement(): Boolean = this is KDocElement || this is PsiWhiteSpace

internal fun Collection<KtNamedDeclaration>.withContext(): Collection<PsiElement> {
    val allElementsToMove = mutableListOf<PsiElement>()
    val first = first()
    val containingFile = first.containingKtFile
    if (size == containingFile.declarations.size) { // when moving all declarations, move all context elements before the first declaration
        allElementsToMove.addAll(first.siblings(forward = false, withSelf = false).toList().filter { it.isContextElement() }.dropWhile { it is PsiWhiteSpace })
    }
    windowed(size = 2) { (prev, next) ->
        allElementsToMove.add(prev)
        val elementsUntilNext = prev.siblings(forward = true, withSelf = false).takeWhileInclusive { it !is KtNamedDeclaration }
        if (next in elementsUntilNext) { // if the next declaration is also moved, move all context elements that are in between declarations
            elementsUntilNext.filterTo(allElementsToMove) { it.isContextElement() }
        }
    }
    val last = last()
    allElementsToMove.add(last)
    if (size == containingFile.declarations.size) { // when moving all declarations, move all context elements after the last declaration
        allElementsToMove.addAll(last.siblings(forward = true, withSelf = false).toList().filter { it.isContextElement() }.dropLastWhile { it is PsiWhiteSpace })
    }
    return allElementsToMove
}

internal fun Iterable<PsiElement>.moveInto(targetFile: KtFile): Map<KtNamedDeclaration, KtNamedDeclaration> {
    val oldToNewMap = mutableMapOf<KtNamedDeclaration, KtNamedDeclaration>()
    forEach { oldElement ->
        val movedElement = targetFile.add(oldElement)
        if (movedElement is KtNamedDeclaration) {
            // we assume that the children are in the same order before and after the move
            for ((oldChild, newChild) in oldElement.withChildDeclarations().zip(movedElement.withChildDeclarations())) {
                oldToNewMap[oldChild] = newChild
            }
        }
    }
    return oldToNewMap
}

internal fun PsiElement.withChildDeclarations() = collectDescendantsOfType<KtNamedDeclaration>().toList()

internal fun K2ChangePackageDescriptor.usageViewDescriptor(): MoveMultipleElementsViewDescriptor {
    return MoveMultipleElementsViewDescriptor(files.toTypedArray(), target.presentablePkgName())
}

internal fun K2MoveOperationDescriptor<*>.usageViewDescriptor(): MoveMultipleElementsViewDescriptor {
    return MoveMultipleElementsViewDescriptor(sourceElements.toTypedArray(), moveDescriptors.first().target.pkgName.presentablePkgName())
}

internal fun FqName.presentablePkgName(): String = if (asString() == "") {
    JavaAnalysisBundle.message("inspection.reference.default.package")
} else asString()

internal fun KtFile.updatePackageDirective(pkgName: FqName) {
    if (pkgName.isRoot) {
        packageDirective?.delete()
    } else {
        val newPackageDirective = KtPsiFactory(project).createPackageDirective(pkgName.quoteIfNeeded())
        packageDirective?.replace(newPackageDirective)
    }
}

internal fun KtFile.updatePackageDirective(destination: PsiDirectory) {
    updatePackageDirective(destination.getFqNameWithImplicitPrefixOrRoot())
}

@JvmOverloads
fun getOrCreateKotlinFile(
    fileName: String,
    targetDir: PsiDirectory,
    packageName: String? = targetDir.getFqNameWithImplicitPrefix()?.asString()
): KtFile =
    (targetDir.findFile(fileName) ?: createKotlinFile(fileName, targetDir, packageName)) as KtFile

fun createKotlinFile(
    fileName: String,
    targetDir: PsiDirectory,
    packageName: String? = targetDir.getFqNameWithImplicitPrefix()?.asString()
): KtFile {
    targetDir.checkCreateFile(fileName)
    val packageFqName = packageName?.let(::FqName) ?: FqName.ROOT
    val file = PsiFileFactory.getInstance(targetDir.project).createFileFromText(
        fileName, KotlinFileType.INSTANCE, if (!packageFqName.isRoot) "package ${packageFqName.quoteIfNeeded()} \n\n" else ""
    )
    return targetDir.add(file) as KtFile
}
