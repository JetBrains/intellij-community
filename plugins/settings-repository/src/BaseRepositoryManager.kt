package org.jetbrains.plugins.settingsRepository

import com.intellij.openapi.util.io.FileUtil

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

public abstract class BaseRepositoryManager protected() : RepositoryManager {
  protected var dir: File = File(getPluginSystemDir(), "repository")

  protected val lock: Any = Object();

  override fun listSubFileNames(path: String): Collection<String> {
    val files = File(dir, path).list()
    if (files == null || files.size == 0) {
      return listOf()
    }
    return listOf(*files)
  }

  override fun deleteRepository() {
    FileUtil.delete(dir)
  }

  throws(javaClass<IOException>())
  override fun read(path: String): InputStream? {
    val file = File(dir, path)
    //noinspection IOResourceOpenedButNotSafelyClosed
    return if (file.exists()) FileInputStream(file) else null
  }

  override fun write(path: String, content: ByteArray, size: Int, async: Boolean) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Write " + path)
    }

    try {
      val file = File(dir, path)
      FileUtil.writeToFile(file, content, 0, size)

      synchronized (lock) {
        addToIndex(file, path, content, size)
      }
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  /**
   * path relative to repository root
   */
  throws(javaClass<Exception>())
  protected abstract fun addToIndex(file: File, path: String, content: ByteArray, size: Int)

  override fun delete(path: String) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Remove " + path)
    }

    try {
      val file = File(dir, path)
      // delete could be called for non-existent file
      if (!file.exists()) {
        return
      }

      val isFile = file.isFile()
      removeFileAndParentDirectoryIfEmpty(file, isFile, dir)

      synchronized (lock) {
        deleteFromIndex(path, isFile)
      }
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  throws(javaClass<Exception>())
  protected abstract fun deleteFromIndex(path: String, isFile: Boolean)

  override fun has(path: String): Boolean {
    return File(dir, path).exists()
  }
}

fun removeFileAndParentDirectoryIfEmpty(file: File, isFile: Boolean, root: File) {
  FileUtil.delete(file)

  if (isFile) {
    // remove empty directories
    var parent: File? = file.getParentFile()
    //noinspection FileEqualsUsage
    while (parent != null && parent != root && parent!!.delete()) {
      parent = parent!!.getParentFile()
    }
  }
}
