package org.jetbrains.kotlin.idea.compiler

import com.intellij.openapi.compiler.CompilableFileTypesProvider
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.kotlin.idea.KotlinFileType

class KotlinCompilableFileTypesProvider : CompilableFileTypesProvider {

    override fun getCompilableFileTypes(): MutableSet<FileType> = mutableSetOf(KotlinFileType.INSTANCE)

}