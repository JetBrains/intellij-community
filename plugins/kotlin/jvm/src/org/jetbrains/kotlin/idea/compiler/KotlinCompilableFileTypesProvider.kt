// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.compiler

import com.intellij.openapi.compiler.CompilableFileTypesProvider
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.kotlin.idea.KotlinFileType

class KotlinCompilableFileTypesProvider : CompilableFileTypesProvider {

    override fun getCompilableFileTypes(): MutableSet<FileType> = mutableSetOf(KotlinFileType.INSTANCE)

}