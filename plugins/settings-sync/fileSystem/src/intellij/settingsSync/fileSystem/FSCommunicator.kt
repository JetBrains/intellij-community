// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package intellij.settingsSync.fileSystem

import com.intellij.openapi.diagnostic.logger
import com.intellij.settingsSync.*
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.getLastModifiedTime

internal class FSCommunicator(override val userId: String) : AbstractServerCommunicator() {

  private val basePath: Path = Path.of(userId)

  companion object {
    private val LOG = logger<FSCommunicator>()
  }

  override fun requestSuccessful() {
    LOG.info("requestSuccessful")
  }

  override fun handleRemoteError(e: Throwable): String {
    LOG.warn("remote error occurred", e)
    return e.message ?: "Remote error occurred: ${e.javaClass.name}"
  }

  override fun readFileInternal(filePath: String): Pair<InputStream?, String?> {
    val path = basePath.resolve(filePath)
    val file = path.toFile()
    if (file.exists()) {
      return Pair(file.inputStream(), path.getLastModifiedTime().toString())
    }
    return Pair(null, null)
  }

  override fun writeFileInternal(filePath: String, versionId: String?, content: InputStream): String? {
    val path = basePath.resolve(filePath)
    if (path.toFile().exists() && path.getLastModifiedTime().toString() != versionId) {
      throw InvalidVersionIdException("Expected versionId is $versionId, but actual is ${path.getLastModifiedTime()}")
    }
    if (!path.parent.toFile().exists()) {
      path.parent.toFile().mkdirs()
    }
    path.toFile().outputStream().use { content.copyTo(it) }
    return path.getLastModifiedTime().toString()
  }

  override fun getLatestVersion(filePath: String): String? {
    val path = basePath.resolve(filePath)
    if (!path.toFile().exists())
      return null
    return path.getLastModifiedTime().toString()
  }

  override fun deleteFileInternal(filePath: String) {
    val path = basePath.resolve(filePath)
    path.toFile().delete()
  }
}