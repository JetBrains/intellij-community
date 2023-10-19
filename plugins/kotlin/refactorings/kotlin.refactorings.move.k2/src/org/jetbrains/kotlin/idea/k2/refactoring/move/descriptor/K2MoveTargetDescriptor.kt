// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor

import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.psi.KtFile

sealed interface K2MoveTargetDescriptor {
    val directory: PsiDirectory

    val pkg: PsiPackage

    open class SourceDirectory(override val pkg: PsiPackage, override val directory: PsiDirectory) : K2MoveTargetDescriptor

    class File(val file: KtFile, pkg: PsiPackage, directory: PsiDirectory) : SourceDirectory(pkg, directory)

    companion object {
        fun SourceDirectory(directory: PsiDirectory): SourceDirectory {
            val pkg = JavaDirectoryService.getInstance().getPackage(directory) ?: error("No package was found")
            return SourceDirectory(pkg, directory)
        }

        fun File(file: KtFile): File {
            val directory = file.containingDirectory ?: error("No containing directory was found")
            val pkgDirective = file.packageDirective?.fqName
            val pkg = JavaPsiFacade.getInstance(file.project).findPackage(pkgDirective?.asString() ?: "")
                ?: error("No package was found")
            return File(file, pkg, directory)
        }
    }
}