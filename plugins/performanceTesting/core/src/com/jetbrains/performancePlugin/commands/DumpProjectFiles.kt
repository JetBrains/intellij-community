// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.diagnostic.dump.IndexContentDiagnosticDumper
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

class DumpProjectFiles(text: String, line: Int) : AbstractCommand(text, line) {

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()

    val dumpProjectFilesDir = getOrCreateDirectoryToDumpFiles()
    LOG.info("DumpFiles will be stored to $dumpProjectFilesDir")

    val project = context.project
    DumbService.getInstance(project).smartInvokeLater {
      object : Task.Modal(project, PerformanceTestingBundle.message("dumping.project.files"), false) {
        override fun run(indicator: ProgressIndicator) {
          try {
            dumpProjectFiles(dumpProjectFilesDir, indicator, project)
            LOG.info("Project files has been successfully stored to $dumpProjectFilesDir")
            actionCallback.setDone()
          }
          catch (e: Throwable) {
            actionCallback.reject(e.message)
          }
        }
      }.queue()
    }
    return actionCallback.toPromise()
  }

  private fun dumpProjectFiles(dumpDir: Path, indicator: ProgressIndicator, project: Project) {
    runReadAction { (FileBasedIndex.getInstance() as FileBasedIndexImpl).flushIndexes() }

    indicator.isIndeterminate = true

    val contentDiagnostic = IndexContentDiagnosticDumper.getIndexContentDiagnosticForProject(project, indicator)
    IndexContentDiagnosticDumper.writeTo(StoreIndices.getFileForDiagnostic(dumpDir), contentDiagnostic)
  }

  companion object {
    internal const val PREFIX = CMD_PREFIX + "dumpProjectFiles"

    private val LOG = logger<DumpProjectFiles>()

    private fun getOrCreateDirectoryToDumpFiles(): Path {
      val property = System.getProperty("dump.project.files.directory")
      return if (property != null) {
        Paths.get(property)
      }
      else {
        try {
          FileUtil.createTempDirectory("projectFiles_dumps", null).toPath()
        }
        catch (e: IOException) {
          throw RuntimeException(e)
        }
      }
    }
  }
}