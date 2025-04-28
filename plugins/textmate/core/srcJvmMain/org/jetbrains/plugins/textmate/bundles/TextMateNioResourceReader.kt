package org.jetbrains.plugins.textmate.bundles

import org.jetbrains.plugins.textmate.getLogger
import org.jetbrains.plugins.textmate.logging.TextMateLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readBytes

class TextMateNioResourceReader(private val directory: Path) : TextMateResourceReader {
  companion object {
    private val LOG: TextMateLogger = getLogger(TextMateNioResourceReader::class)
  }

  override fun list(relativePath: String): List<String> {
    return runCatching {
      Files.list(directory.resolve(relativePath)).use { stream ->
        stream.filter { it.isRegularFile() }.map { it.name }.toList()
      }
    }.getOrElse { e ->
      when (e) {
        is NoSuchFileException -> {}
        else -> LOG.warn(e) { "Can't load plists from `$relativePath`" }
      }
      emptyList()
    }
  }

  override fun read(relativePath: String): ByteArray? {
    return try {
      directory.resolve(relativePath).readBytes()
    }
    catch (_: NoSuchFileException) {
      LOG.warn { "Cannot find referenced file `$relativePath` in bundle `$directory`" }
      null
    }
    catch (e: Throwable) {
      LOG.warn(e) { "Cannot read referenced file `$relativePath` in bundle `$directory`" }
      null
    }
  }
}