// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.psi.*
import org.jetbrains.kotlin.psi.KtFile

fun K2MoveTarget(element: PsiElement) = when(element) {
    is KtFile -> K2MoveTarget.File(element)
    is PsiDirectory -> K2MoveTarget.Directory(element)
    else -> error("Unsupported move target")
}

sealed interface K2MoveTarget {
    /**
     * Can be null if the directory only exists in memory.
     */
    val directory: PsiDirectory?

    val pkg: PsiPackage

    class File(val file: KtFile, override val pkg: PsiPackage, override val directory: PsiDirectory?) : K2MoveTarget

    class Directory(override val pkg: PsiPackage, override val directory: PsiDirectory) : K2MoveTarget

    companion object {
        fun File(file: KtFile): File? {
            val directory = file.containingDirectory ?: return null
            val pkgDirective = file.packageDirective?.fqName
            val pkg = JavaPsiFacade.getInstance(file.project).findPackage(pkgDirective?.asString() ?: "")
                ?: error("No package was found")
            return File(file, pkg, directory)
        }

        fun Directory(directory: PsiDirectory): Directory {
            val pkg = JavaDirectoryService.getInstance().getPackage(directory) ?: error("No package was found")
            return Directory(pkg, directory)
        }
    }
}