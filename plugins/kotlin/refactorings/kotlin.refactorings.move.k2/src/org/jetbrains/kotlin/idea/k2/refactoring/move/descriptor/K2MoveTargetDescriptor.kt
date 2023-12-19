// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor

import com.intellij.psi.PsiDirectory
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.kotlin.idea.k2.refactoring.getOrCreateKotlinFile
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

sealed interface K2MoveTargetDescriptor {
    val directory: PsiDirectory

    val pkgName: FqName

    open class SourceDirectory(override val pkgName: FqName, override val directory: PsiDirectory) : K2MoveTargetDescriptor

    class File(val fileName: String, pkgName: FqName, directory: PsiDirectory) : SourceDirectory(pkgName, directory) {
        @RequiresWriteLock
        fun getOrCreateFile(): KtFile = getOrCreateKotlinFile(fileName, directory, pkgName.asString())
    }

    companion object {
        fun File(file: KtFile): File {
            val directory = file.containingDirectory ?: error("No containing directory was found")
            return File(file.name, file.packageFqName, directory)
        }
    }
}