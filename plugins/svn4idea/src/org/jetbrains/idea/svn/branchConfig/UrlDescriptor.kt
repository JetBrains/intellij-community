// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig

import com.intellij.util.io.KeyDescriptor
import org.jetbrains.idea.svn.SvnUtil.createUrl
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.commandLine.SvnBindException
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

class UrlDescriptor(val isEncoded: Boolean) : KeyDescriptor<Url> {
  @Throws(IOException::class)
  override fun save(out: DataOutput, value: Url): Unit = out.writeUTF(if (isEncoded) value.toString() else value.toDecodedString())

  @Throws(IOException::class)
  override fun read(`in`: DataInput): Url {
    val urlValue = `in`.readUTF()

    return try {
      createUrl(urlValue, isEncoded)
    }
    catch (e: SvnBindException) {
      throw IOException("Could not parse url $urlValue", e)
    }
  }

  override fun getHashCode(value: Url): Int = value.hashCode()

  override fun isEqual(val1: Url, val2: Url): Boolean = val1 == val2

  companion object {
    @JvmField
    val ENCODED_URL_DESCRIPTOR: UrlDescriptor = UrlDescriptor(true)
    @JvmField
    val DECODED_URL_DESCRIPTOR: UrlDescriptor = UrlDescriptor(false)
  }
}