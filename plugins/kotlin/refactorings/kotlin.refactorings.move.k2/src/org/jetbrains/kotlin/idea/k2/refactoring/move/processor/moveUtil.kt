// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.java.analysis.JavaAnalysisBundle
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefixOrRoot
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2ChangePackageDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal fun Iterable<KtNamedDeclaration>.moveInto(targetFile: KtFile): Map<KtNamedDeclaration, KtNamedDeclaration> {
    val oldToNewMap = mutableMapOf<KtNamedDeclaration, KtNamedDeclaration>()
    forEach { oldMovedDeclaration ->
        val newMovedDeclaration = targetFile.add(oldMovedDeclaration) as KtNamedDeclaration
        // we assume that the children are in the same order before and after the move
        for ((oldChild, newChild) in oldMovedDeclaration.withChildDeclarations().zip(newMovedDeclaration.withChildDeclarations())) {
            oldToNewMap[oldChild] = newChild
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
