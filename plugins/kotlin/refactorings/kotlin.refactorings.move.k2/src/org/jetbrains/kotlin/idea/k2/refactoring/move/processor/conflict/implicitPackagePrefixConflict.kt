// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.getImplicitPackagePrefix
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.util.sourceRoot
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

internal fun checkImplicitPackagePrefixConflict(
    targetDir: PsiDirectory,
    targetPkg: FqName,
): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()
    val implicitPrefix = targetDir.getImplicitPackagePrefix() ?: return conflicts

    if (!targetPkg.startsWith(implicitPrefix)) {
        val referenceFile = targetDir.firstKtFileInSourceRootWithPrefix(implicitPrefix) ?: return conflicts
        val packageDirectiveInReferenceFile = referenceFile.packageDirective ?: return conflicts
        conflicts.putValue(packageDirectiveInReferenceFile, KotlinBundle.message(
            "the.package.0.does.not.match.the.implicit.prefix.1",
            targetPkg.asString(), implicitPrefix.asString()
        ))
    }
    return conflicts
}

/**
 * Find the first [KtFile] in the source root this directory belongs to or in any of the root's subdirectories.
 * Since the [PsiDirectory] has an implicit package prefix, at least one Kotlin file in the source root should exist.
 */
private fun PsiDirectory.firstKtFileInSourceRootWithPrefix(prefix: FqName): KtFile? {
    val sourceRoot = sourceRoot ?: return null
    val project = project
    val psiManager = PsiManager.getInstance(project)
    return sourceRoot.firstKtFileInSourceRootWithPrefix(project, psiManager, prefix)
}

private fun VirtualFile.firstKtFileInSourceRootWithPrefix(project: Project, psiManager: PsiManager, prefix: FqName): KtFile? {
    if (!isDirectory) return null
    val candidateFiles = children.filter { it.nameSequence.endsWith(KotlinFileType.EXTENSION) }
    candidateFiles.forEach { virtualFile ->
        psiManager.findFile(virtualFile)?.asKtFileWithImplicitPrefix(prefix)?.let { return it }
    }
    children.filter { it.isDirectory }.forEach { directory ->
        directory.firstKtFileInSourceRootWithPrefix(project, psiManager, prefix)?.let { return it }
    }
    return null
}

private fun PsiFile.asKtFileWithImplicitPrefix(implicitPrefix: FqName): KtFile? =
    (this as? KtFile).takeIf { it?.packageDirective?.fqName?.startsWith(implicitPrefix) == true }
