package com.intellij.python.pyproject.model.internal.autoImportBridge

import com.intellij.python.pyproject.model.internal.pyProjectToml.FSWalkInfo
import com.intellij.python.pyproject.model.internal.pyProjectToml.walkFileSystem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path

internal class FsInfoStorage(private val projectRootDir: Path) {
  private val updateMut = Mutex()

  private var cache: FSWalkInfo? = null


  suspend fun getFsInfo(forceRefresh: Boolean): FSWalkInfo {
    updateMut.withLock {
      val cache = cache
      if (cache != null && !forceRefresh) {
        return cache
      }
      val fsInfo = walkFileSystem(projectRootDir)
      this.cache = fsInfo
      return fsInfo
    }
  }
}