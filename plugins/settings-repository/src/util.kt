package org.jetbrains.settingsRepository

import com.intellij.openapi.util.text.StringUtil
import java.nio.ByteBuffer

public fun String?.nullize(): String? = StringUtil.nullize(this)

public fun byteBufferToBytes(byteBuffer: ByteBuffer): ByteArray {
  if (byteBuffer.hasArray() && byteBuffer.arrayOffset() == 0) {
    val bytes = byteBuffer.array()
    if (bytes.size == byteBuffer.limit()) {
      return bytes
    }
  }

  val bytes = ByteArray(byteBuffer.limit())
  byteBuffer.get(bytes)
  return bytes
}