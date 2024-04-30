// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.java.analysis.JavaAnalysisBundle
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileFactory
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2ChangePackageDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory

internal fun Set<KtNamedDeclaration>.moveInto(targetFile: KtFile): Map<KtNamedDeclaration, KtNamedDeclaration> {
    return associateWith { declaration -> targetFile.add(declaration) as KtNamedDeclaration }
}

internal fun K2ChangePackageDescriptor.usageViewDescriptor(): MoveMultipleElementsViewDescriptor {
    return MoveMultipleElementsViewDescriptor(files.toTypedArray(), target.presentablePkgName())
}

internal fun K2MoveDescriptor.usageViewDescriptor(): MoveMultipleElementsViewDescriptor {
    return MoveMultipleElementsViewDescriptor(source.elements.toTypedArray(), target.pkgName.presentablePkgName())
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
    val newPackageName = JavaDirectoryService.getInstance().getPackage(destination)?.kotlinFqName ?: return
    updatePackageDirective(newPackageName)
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
