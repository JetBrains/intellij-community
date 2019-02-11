// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index

import com.google.common.hash.HashCode
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.io.*
import junit.framework.TestCase
import java.io.DataInput
import java.io.DataOutput
import java.io.File

interface ContentHashVerifier {
  companion object {
    val DEAF = object : ContentHashVerifier {
      override fun checkContent(hash: HashCode, newFileContent: FileContentImpl) {}

      override fun putContent(hash: HashCode, fileContent: FileContentImpl) {}

      override fun dispose() {}
    }
  }

  @Throws(AssertionError::class)
  fun checkContent(hash: HashCode, newFileContent: FileContentImpl)

  fun putContent(hash: HashCode, fileContent: FileContentImpl)

  fun dispose()
}

class InMemoryContentHashVerifier: ContentHashVerifier {
  val map = ContainerUtil.newHashMap<HashCode, CharSequence>()

  override fun checkContent(hash: HashCode, newFileContent: FileContentImpl) {
    TestCase.assertEquals(map[hash], newFileContent.contentAsText)
  }

  override fun putContent(hash: HashCode, fileContent: FileContentImpl) {
    map[hash] = fileContent.contentAsText
  }

  override fun dispose() {}
}

class OnDiskContentHashVerifier(dir: String) : ContentHashVerifier {
  private val indexDir = FileUtil.join(dir, "content.hash")
  private val map: PersistentMap<HashCode, String> = PersistentHashMap(File(indexDir), object : KeyDescriptor<HashCode> {
    override fun read(`in`: DataInput): HashCode {
        return HashCode.fromString(EnumeratorStringDescriptor.INSTANCE.read(`in`))
    }

    override fun save(out: DataOutput, value: HashCode?) {
      EnumeratorStringDescriptor.INSTANCE.save(out, value.toString())
    }

    override fun getHashCode(value: HashCode?): Int {
      return value.hashCode()
    }

    override fun isEqual(val1: HashCode?, val2: HashCode?): Boolean {
      return val1 == val2
    }
  }, EnumeratorStringDescriptor.INSTANCE)

  override fun checkContent(hash: HashCode, newFileContent: FileContentImpl) {
  }

  override fun putContent(hash: HashCode, fileContent: FileContentImpl) {
  }

  override fun dispose() {
    try {
      map.close()
    } finally {
      IOUtil.deleteAllFilesStartingWith(File(indexDir))
    }
  }
}