package org.jetbrains.settingsRepository

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeProvider2
import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.openapi.vcs.merge.MultipleFileMergeDialog
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.PathUtilRt
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Arrays

public abstract class BaseRepositoryManager() : RepositoryManager {
  protected var dir: File = File(getPluginSystemDir(), "repository")

  protected val lock: Any = Object();

  override fun listSubFileNames(path: String): Collection<String> {
    val files = File(dir, path).list()
    if (files == null || files.size() == 0) {
      return listOf()
    }
    return listOf(*files)
  }

  override fun deleteRepository() {
    FileUtil.delete(dir)
  }

  override fun read(path: String): InputStream? {
    synchronized (lock) {
      val file = File(dir, path)
      // we ignore empty files as well - delete if corrupted
      if (file.length() == 0L) {
        if (file.exists()) {
          try {
            LOG.warn("File $path is empty (length 0), will be removed")
            delete(file, path)
          }
          catch (e: Exception) {
            LOG.error(e)
          }
        }
        return null
      }
      return FileInputStream(file)
    }
  }

  override fun write(path: String, content: ByteArray, size: Int) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Write $path")
    }

    try {
      synchronized (lock) {
        val file = File(dir, path)
        FileUtil.writeToFile(file, content, 0, size)

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
  protected abstract fun addToIndex(file: File, path: String, content: ByteArray, size: Int)

  override fun delete(path: String) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Remove " + path)
    }

    try {
      synchronized (lock) {
        val file = File(dir, path)
        // delete could be called for non-existent file
        if (!file.exists()) {
          return
        }

        delete(file, path)
      }
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  private fun delete(file: File, path: String) {
    val isFile = file.isFile()
    removeFileAndParentDirectoryIfEmpty(file, dir, isFile)
    deleteFromIndex(path, isFile)
  }

  protected abstract fun deleteFromIndex(path: String, isFile: Boolean)

  override fun has(path: String): Boolean {
    synchronized (lock) {
      return File(dir, path).exists()
    }
  }
}

fun removeFileAndParentDirectoryIfEmpty(file: File, root: File, isFile: Boolean = true) {
  FileUtil.delete(file)

  if (isFile) {
    // remove empty directories
    var parent: File? = file.getParentFile()
    //noinspection FileEqualsUsage
    while (parent != null && parent != root && parent.delete()) {
      parent = parent.getParentFile()
    }
  }
}
// kotlin bug, cannot be val (.NoSuchMethodError: org.jetbrains.settingsRepository.SettingsRepositoryPackage.getMARKER_ACCEPT_MY()[B)
TestOnly object AM {
  val MARKER_ACCEPT_MY: ByteArray = "__accept my__".toByteArray()
  val MARKER_ACCEPT_THEIRS: ByteArray = "__accept theirs__".toByteArray()
}

fun resolveConflicts(files: List<VirtualFile>, mergeProvider: MergeProvider2): List<VirtualFile> {
  if (ApplicationManager.getApplication()!!.isUnitTestMode()) {
    val mergeSession = mergeProvider.createMergeSession(files)
    for (file in files) {
      val mergeData = mergeProvider.loadRevisions(file)
      if (Arrays.equals(mergeData.CURRENT, AM.MARKER_ACCEPT_MY) || Arrays.equals(mergeData.LAST, AM.MARKER_ACCEPT_THEIRS)) {
        mergeSession.conflictResolvedForFile(file, MergeSession.Resolution.AcceptedYours)
      }
      else if (Arrays.equals(mergeData.CURRENT, AM.MARKER_ACCEPT_THEIRS) || Arrays.equals(mergeData.LAST, AM.MARKER_ACCEPT_MY)) {
        mergeSession.conflictResolvedForFile(file, MergeSession.Resolution.AcceptedTheirs)
      }
      else if (Arrays.equals(mergeData.LAST, AM.MARKER_ACCEPT_MY)) {
        file.setBinaryContent(mergeData.LAST!!)
        mergeProvider.conflictResolvedForFile(file)
      }
      else {
        throw UnsupportedOperationException()
      }
    }

    return files
  }

  var processedFiles: List<VirtualFile>? = null
  invokeAndWaitIfNeed {
    val fileMergeDialog = MultipleFileMergeDialog(null, files, mergeProvider, MergeDialogCustomizer())
    fileMergeDialog.show()
    processedFiles = fileMergeDialog.getProcessedFiles()
  }
  return processedFiles!!
}

class RepositoryVirtualFile(private val path: String) : LightVirtualFile(PathUtilRt.getFileName(path), StdFileTypes.XML, "", CharsetToolkit.UTF8_CHARSET, 1L) {
  var content: ByteArray? = null
    private set

  override fun getPath() = path

  override fun setBinaryContent(content: ByteArray, newModificationStamp: Long, newTimeStamp: Long, requestor: Any?) {
    $content = content
  }

  override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
    throw IllegalStateException("You must use setBinaryContent")
  }

  override fun setContent(requestor: Any?, content: CharSequence?, fireEvent: Boolean) {
    throw IllegalStateException("You must use setBinaryContent")
  }
}