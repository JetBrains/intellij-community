package com.intellij.mcpserver.util

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.nio.file.Path

suspend fun LocalFileSystem.refreshIoFilesAsync(vararg files: File, recursive: Boolean = false) {
  val finished = CompletableDeferred<Unit>()
  refreshIoFiles(files.toList(), true, recursive) {
    finished.complete(Unit)
  }
  finished.await()
}

suspend fun LocalFileSystem.refreshNioFilesAsync(vararg files: Path, recursive: Boolean = false) {
  val finished = CompletableDeferred<Unit>()
  refreshNioFiles(files.toList(), true, recursive) {
    finished.complete(Unit)
  }
  finished.await()
}

suspend fun LocalFileSystem.refreshFilesAsync(vararg files: VirtualFile, recursive: Boolean = false) {
  val finished = CompletableDeferred<Unit>()
  refreshFiles(files.toList(), true, recursive) {
    finished.complete(Unit)
  }
  finished.await()
}