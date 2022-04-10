// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinder
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import java.io.InputStream

class CompoundVirtualFileFinder(private val finders: List<VirtualFileFinder>) : VirtualFileFinder() {
    override fun findBuiltInsData(packageFqName: FqName): InputStream? {
        for (finder in finders) {
            finder.findBuiltInsData(packageFqName)?.let { return it }
        }

        return null
    }

    override fun findMetadata(classId: ClassId): InputStream? {
        for (finder in finders) {
            finder.findMetadata(classId)?.let { return it }
        }

        return null
    }

    override fun findSourceOrBinaryVirtualFile(classId: ClassId): VirtualFile? = findVirtualFileWithHeader(classId)

    override fun findVirtualFileWithHeader(classId: ClassId): VirtualFile? {
        for (finder in finders) {
            finder.findVirtualFileWithHeader(classId)?.let { return it }
        }

        return null
    }

    override fun hasMetadataPackage(fqName: FqName): Boolean {
        for (finder in finders) {
            if (finder.hasMetadataPackage(fqName)) {
                return true
            }
        }

        return false
    }
}