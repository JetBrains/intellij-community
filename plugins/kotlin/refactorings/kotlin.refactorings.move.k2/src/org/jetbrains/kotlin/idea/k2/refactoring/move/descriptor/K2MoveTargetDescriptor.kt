// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefixOrRoot
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.k2.refactoring.getOrCreateKotlinFile
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
     * Gets or creates the target location, like a file or directory. It might be the case that the target directory or target file doesn't
     * exist yet. In this case this method will create the file or directory based on the [baseDirectory] and [pkgName]. The creation of
     * the target is thus package aware, example:
     *  ```
     *  src/
     *      Foo.kt <--- root pkg
     *      a/Bar.kt
     *  ```
     *  If we move `Bar.kt` to `src/b` and change the pkg to `b``the [baseDirectory] will be `src` but because the package is `b` we will
     *  and `Foo.kt` is in the root package we will create `src/b`.
     *
     *  This also works when the project structure doesn't match the directory structure:
     *  ```
     *  src/
     *      Foo.kt <--- package c.d
     *      a/Bar.kt
     *  ```
     *  If we move `Bar.kt` to `src/b` and change the pkg to `c.d.b``the [baseDirectory] will be `src` but because the package is `c.d.b`
     *  and `Foo.kt` has package c.d we won't create directory `src/c/d/b` but instead create src/d.
     */
    @RequiresWriteLock
    fun getOrCreateTarget(): PsiFileSystemItem

    open class SourceDirectory(
        override val pkgName: FqName,
        override val baseDirectory: PsiDirectory
    ) : K2MoveTargetDescriptor {
        override fun getOrCreateTarget(): PsiFileSystemItem {
            val basePkg = baseDirectory.getFqNameWithImplicitPrefixOrRoot()
            return if (basePkg != pkgName) {
                val pkgDiff = pkgName.asString()
                    .removePrefix(basePkg.asString())
                    .replace(".", "/")
                VfsUtil.createDirectoryIfMissing(baseDirectory.virtualFile, pkgDiff)
                    .toPsiDirectory(baseDirectory.project)
                    ?: error("Could not create directory structure")
            } else baseDirectory
        }
    }

    class File(
        val fileName: String,
        pkgName: FqName,
        baseDirectory: PsiDirectory
    ) : SourceDirectory(pkgName, baseDirectory) {
        override fun getOrCreateTarget(): KtFile {
            val directory = super.getOrCreateTarget() as PsiDirectory
            return getOrCreateKotlinFile(fileName, directory, pkgName.asString())
        }
    }

    companion object {
        fun File(file: KtFile): File {
            val directory = file.containingDirectory ?: error("No containing directory was found")
            return File(file.name, file.packageFqName, directory)
        }
    }
}