// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.internal

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WritingAccessProvider
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem

class DecompiledFileWritingAccessProvider : WritingAccessProvider() {
    override fun isPotentiallyWritable(file: VirtualFile): Boolean {
        if (file.fileSystem is DummyFileSystem && file.parent?.name == KOTLIN_DECOMPILED_FOLDER) {
            return false
        }
        return true
    }

    override fun requestWriting(vararg files: VirtualFile): Collection<VirtualFile> = emptyList()
}