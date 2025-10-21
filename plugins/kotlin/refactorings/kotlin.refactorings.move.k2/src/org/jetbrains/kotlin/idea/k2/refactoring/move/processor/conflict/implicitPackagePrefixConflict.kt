// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.getImplicitPackagePrefix
import org.jetbrains.kotlin.idea.util.sourceRoot
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

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
    val containingModule = ProjectFileIndex.getInstance(project).getModuleForFile(sourceRoot) ?: return null

    var file: KtFile? = null
    FileTypeIndex.processFiles(KotlinFileType.INSTANCE, Processor { vFile ->
        val ktFile = vFile.toKtFileIfItsPackageHasPrefix(prefix, project)
        if (ktFile != null) {
            file = ktFile
            false
        } else {
            true
        }
    }, GlobalSearchScope.moduleScope(containingModule))

    return file
}

private fun VirtualFile.toKtFileIfItsPackageHasPrefix(prefix: FqName, project: Project): KtFile? {
    if (!nameSequence.endsWith(KotlinFileType.EXTENSION)) return null
    val ktFile = PsiManager.getInstance(project).findFile(this) as? KtFile ?: return null
    return ktFile.takeIf { it.packageDirective?.fqName?.startsWith(prefix) == true }
}
