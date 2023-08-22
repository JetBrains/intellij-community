// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvmDecompiler

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WritingAccessProvider
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem

class DecompiledFileWritingAccessProvider : WritingAccessProvider() {
    override fun isPotentiallyWritable(file: VirtualFile): Boolean =
        !(file.fileSystem is DummyFileSystem && file.parent?.name == KotlinBytecodeDecompilerTask.KOTLIN_DECOMPILED_FOLDER)

    override fun requestWriting(files: Collection<VirtualFile>): Collection<VirtualFile> = emptyList()
}