package com.intellij.mcpserver

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Gets files ready for `lint_files` before the daemon main passes run on them.
 * A language-server client, for example, opens the files on the server and waits for its diagnostics.
 */
@ApiStatus.Experimental
interface McpLintFilesPreparer {
  companion object {
    val EP: ExtensionPointName<McpLintFilesPreparer> = ExtensionPointName
      .create("com.intellij.mcpServer.lintFilesPreparer")
  }

  /**
   * Starts to get [files] ready and returns without waiting for them.
   * The result is closed when the lint request is done with the files.
   */
  suspend fun prepare(project: Project, files: List<VirtualFile>): McpLintFilesPreparation
}

@ApiStatus.Experimental
interface McpLintFilesPreparation : AutoCloseable {
  /**
   * Suspends until [file] is ready for the daemon main passes; returns at once for a file the preparer does not handle.
   * Lint analyzes each file as soon as it is ready, bounds the wait by the request deadline,
   * and reports a file not ready in time as timed out, never as clean.
   */
  suspend fun awaitReady(file: VirtualFile)
}
