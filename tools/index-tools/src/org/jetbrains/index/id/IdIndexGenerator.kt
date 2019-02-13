// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index.id

import com.google.common.hash.HashCode
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.stubs.HashCodeDescriptor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.io.MapDataExternalizer
import com.intellij.util.io.PersistentHashMap
import org.jetbrains.index.RootsPrebuiltIndexer
import org.jetbrains.index.SingleIndexGenerator
import java.io.File

/**
 * @author traff
 */

open class IdIndexGenerator : SingleIndexGeneratorImpl<IdIndexEntry, Int>(IdIndex.EXTENSION_POINT_NAME.findExtension(IdIndex::class.java)!!) {
  fun buildIdIndexForRoots(rootFiles: Collection<VirtualFile>, idIndexStorageFilePath: String) {
    RootsPrebuiltIndexer(rootFiles).buildIndex(arrayOf(this), idIndexStorageFilePath)
  }
}

open class SingleIndexGeneratorImpl<K, V>(private val indexExtension: FileBasedIndexExtension<K, V>) : SingleIndexGenerator<Map<K, V>>() {
  private val suitableFleTypes: MutableSet<FileType>

  init {
    val inputFilter = indexExtension.inputFilter
    suitableFleTypes = mutableSetOf()
    if (inputFilter is FileBasedIndex.FileTypeSpecificInputFilter) {
      inputFilter.registerFileTypesUsedForIndexing { suitableFleTypes.add(it)}
    }
  }

  override val internalName: String
    get() = indexExtension.name.name

  override fun getIndexValue(fileContent: FileContentImpl): Map<K, V>? {
    return indexExtension.indexer.map(fileContent)
  }

  override fun createStorage(storageFilePath: String): PersistentHashMap<HashCode, Map<K, V>> {
    return PersistentHashMap(File(storageFilePath, internalName + ".input"),
                             HashCodeDescriptor.instance,
                             MapDataExternalizer(indexExtension.keyDescriptor, indexExtension.valueExternalizer))
  }

  override val fileFilter: VirtualFileFilter
    get() = VirtualFileFilter { file -> indexExtension.inputFilter.acceptInput(file!!) && suitableFleTypes.contains(file.fileType) }
}