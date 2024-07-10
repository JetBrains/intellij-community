// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor

import com.intellij.ide.util.DirectoryUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefixOrRoot
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.getOrCreateKotlinFile
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

sealed interface K2MoveTargetDescriptor {
    /**
     * Either the target directory where the file or element will be moved to or the closest possible directory. The real target directory
     * can be created by calling `getOrCreateTarget`.
     */
    val baseDirectory: PsiDirectory

    val pkgName: FqName


    /**
     * Gets or creates the target
     */
    @RequiresWriteLock
    fun getOrCreateTarget(dirStructureMatchesPkg: Boolean): PsiFileSystemItem

    open class Directory(
        override val pkgName: FqName,
        override val baseDirectory: PsiDirectory
    ) : K2MoveTargetDescriptor {
        override fun getOrCreateTarget(dirStructureMatchesPkg: Boolean): PsiFileSystemItem {
            if (!dirStructureMatchesPkg) return baseDirectory
            val implicitPkgPrefix = baseDirectory.getFqNameWithImplicitPrefixOrRoot()
            val pkgSuffix = pkgName.asString().removePrefix(implicitPkgPrefix.asString()).removePrefix(".")
            val file = VfsUtilCore.findRelativeFile(pkgSuffix.replace('.', java.io.File.separatorChar), baseDirectory.virtualFile)
            if (file != null) return file.toPsiDirectory(baseDirectory.project) ?: error("Could not find directory $pkgName")
            return DirectoryUtil.createSubdirectories(pkgSuffix, baseDirectory, ".")
        }
    }

    class File(
        val fileName: String,
        pkgName: FqName,
        baseDirectory: PsiDirectory
    ) : Directory(pkgName, baseDirectory) {
        override fun getOrCreateTarget(dirStructureMatchesPkg: Boolean): KtFile {
            val directory = super.getOrCreateTarget(dirStructureMatchesPkg) as PsiDirectory
            return getOrCreateKotlinFile(fileName, directory, pkgName.asString())
        }
    }

    companion object {
        fun Directory(directory: PsiDirectory): Directory {
            return Directory(directory.getFqNameWithImplicitPrefixOrRoot(), directory)
        }

        fun File(file: KtFile): File {
            val directory = file.containingDirectory ?: error("No containing directory was found")
            return File(file.name, file.packageFqName, directory)
        }
    }
}