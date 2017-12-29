// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index.id

import com.google.common.hash.HashCode
import com.intellij.index.IdIndexMapDataExternalizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.impl.cache.impl.id.IdIndexers
import com.intellij.psi.stubs.HashCodeDescriptor
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.io.PersistentHashMap
import org.jetbrains.index.IndexGenerator
import java.io.File

/**
 * @author traff
 */

open class IdIndexGenerator(private val idIndexStorageFilePath: String) : IndexGenerator<Map<IdIndexEntry, Int>>(idIndexStorageFilePath) {
  override fun getIndexValue(fileContent: FileContentImpl): Map<IdIndexEntry, Int> {
    return IdIndexers.INSTANCE.forFileType(fileContent.file.fileType).map(fileContent)
  }

  override fun createStorage(stubsStorageFilePath: String): PersistentHashMap<HashCode, Map<IdIndexEntry, Int>> {
    return PersistentHashMap(File(idIndexStorageFilePath + ".input"),
                             HashCodeDescriptor.instance, IdIndexMapDataExternalizer())
  }

  fun buildIdIndexForRoots(rootFiles: List<VirtualFile>) {
    buildIndexForRoots(rootFiles)
  }
}
