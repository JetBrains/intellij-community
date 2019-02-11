// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index.id

import com.google.common.hash.HashCode
import com.intellij.index.IdIndexMapDataExternalizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.impl.cache.impl.id.IdIndexers
import com.intellij.psi.stubs.HashCodeDescriptor
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.io.PersistentHashMap
import org.jetbrains.index.PrebuiltIndexer
import org.jetbrains.index.RootsPrebuiltIndexer
import org.jetbrains.index.SingleIndexGenerator
import java.io.File

/**
 * @author traff
 */

open class IdIndexGenerator : SingleIndexGenerator<Map<IdIndexEntry, Int>>() {
  override val internalName: String
    get() = "id.indexers"

  override fun getIndexValue(fileContent: FileContentImpl): Map<IdIndexEntry, Int> {
    return IdIndexers.INSTANCE.forFileType(fileContent.file.fileType).map(fileContent)
  }

  override fun createStorage(storageFilePath: String): PersistentHashMap<HashCode, Map<IdIndexEntry, Int>> {
    return PersistentHashMap(File("$storageFilePath.input"),
                             HashCodeDescriptor.instance, IdIndexMapDataExternalizer())
  }

  fun buildIdIndexForRoots(rootFiles: Collection<VirtualFile>, idIndexStorageFilePath: String) {
    RootsPrebuiltIndexer(rootFiles).buildIndex(arrayOf(this), idIndexStorageFilePath)
  }
}
