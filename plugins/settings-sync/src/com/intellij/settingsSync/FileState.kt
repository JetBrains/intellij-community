package com.intellij.settingsSync

import com.intellij.util.io.readBytes
import com.intellij.util.io.systemIndependentPath
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.relativeTo

internal sealed class FileState(open val file: String) {

  class Modified(override val file: String, val content: ByteArray, val size: Int) : FileState(file) {
    override fun toString(): String = "file='$file', content:\n${String(content, StandardCharsets.UTF_8)}"

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Modified

      if (file != other.file) return false
      if (!content.contentEquals(other.content)) return false
      if (size != other.size) return false

      return true
    }

    override fun hashCode(): Int {
      var result = file.hashCode()
      result = 31 * result + content.contentHashCode()
      result = 31 * result + size
      return result
    }
  }

  data class Deleted(override val file: String): FileState(file)
}

internal fun getFileStateFromFileWithDeletedMarker(file: Path, storageBasePath: Path): FileState {
  val bytes = file.readBytes()
  val text = String(bytes, Charset.defaultCharset())
  val fileSpec = file.relativeTo(storageBasePath).systemIndependentPath
  return if (text == DELETED_FILE_MARKER) {
    FileState.Deleted(fileSpec)
  } else {
    FileState.Modified(fileSpec, bytes, bytes.size)
  }
}

internal const val DELETED_FILE_MARKER = "DELETED"