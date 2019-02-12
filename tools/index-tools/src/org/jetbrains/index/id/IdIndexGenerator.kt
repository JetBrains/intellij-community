// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index.id

import com.google.common.hash.HashCode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.stubs.HashCodeDescriptor
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.PersistentHashMap
import org.jetbrains.index.RootsPrebuiltIndexer
import org.jetbrains.index.SingleIndexGenerator
import java.io.DataInput
import java.io.DataOutput
import java.io.File

/**
 * @author traff
 */

open class IdIndexGenerator : SingleIndexGeneratorImpl<IdIndexEntry, Int>(IdIndex.EXTENSION_POINT_NAME.findExtension(IdIndex::class.java)!!) {
  fun buildIdIndexForRoots(rootFiles: Collection<VirtualFile>, idIndexStorageFilePath: String) {
    RootsPrebuiltIndexer(rootFiles).buildIndex(arrayOf(this), idIndexStorageFilePath)
  }
}

open class SingleIndexGeneratorImpl<K, V>(private val indexExtension: FileBasedIndexExtension<K, V>): SingleIndexGenerator<Map<K, V>>() {
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

}

private class MapDataExternalizer<K, V>(private val keyExternalizer: DataExternalizer<K>,
                                        private val valueExternalizer: DataExternalizer<V>) : DataExternalizer<Map<K, V>> {
  override fun save(out: DataOutput, value: Map<K, V>) {
    DataInputOutputUtil.writeINT(out, value.size)
    for (e in value.entries) {
      keyExternalizer.save(out, e.key)
      valueExternalizer.save(out, e.value)
    }
  }

  override fun read(`in`: DataInput): Map<K, V> {
    val size = DataInputOutputUtil.readINT(`in`)
    val map = HashMap<K, V>()
    for (i in 0 until size) {
      val key = keyExternalizer.read(`in`)
      val value = valueExternalizer.read(`in`)
      map[key] = value
    }
    return map
  }

}