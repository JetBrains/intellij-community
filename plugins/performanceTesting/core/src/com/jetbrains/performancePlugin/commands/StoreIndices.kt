// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.execution.runners.ExecutionUtil.PROPERTY_DYNAMIC_CLASSPATH
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.stubs.SerializationManagerEx
import com.intellij.util.indexing.FileBasedIndexTumbler
import com.intellij.util.indexing.diagnostic.dump.IndexContentDiagnosticDumper
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Command stores created indices.
 * Stores created during indexing indices in the defined by the parameter '-Dstore.indices.command.stored.indexes.dump.directory' directory.
 */
class StoreIndices(text: String, line: Int) : AbstractCommand(text, line) {
  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    val project = context.project
    LOG.info(PROPERTY_DYNAMIC_CLASSPATH + ": " + PropertiesComponent.getInstance(project).getValue(PROPERTY_DYNAMIC_CLASSPATH))

    val storedIndexDir = getOrCreateDirectoryToStoreIndex()
    LOG.info("Index will be stored to $storedIndexDir")

    DumbService.getInstance(project).smartInvokeLater {
      val switcher = FileBasedIndexTumbler("Storing indexes command")
      switcher.turnOff()
      var error: Throwable? = null
      val storeIndexesAction: () -> Unit = {
        try {
          storeIndexesTo(storedIndexDir,
                         ProgressManager.getInstance().progressIndicator,
                         project)
          LOG.info("Indices have been successfully stored to $storedIndexDir")
        }
        catch (e: Throwable) {
          error = e
        }
      }
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
        storeIndexesAction,
        PerformanceTestingBundle.message("storing.indexes"),
        false,
        project
      )

      switcher.turnOn(null)

      if (error != null) {
        LOG.error(error!!)
        actionCallback.reject(error!!.message)
      }
      else {
        actionCallback.setDone()
      }
    }
    return actionCallback.toPromise()
  }

  private fun storeIndexesTo(storedIndexDir: Path, indicator: ProgressIndicator, project: Project) {
    runReadAction { SerializationManagerEx.getInstanceEx().flushNameStorage() }

    indicator.isIndeterminate = true
    indicator.text = PerformanceTestingBundle.message("storing.indexes.copying")

    // Brutally copy all current indexes. They may contain data irrelevant to the current project.
    // To ignore irrelevant data we may later use [IndexedFilePaths#indexedFileProviderDebugNameToOriginalFileIds]
    // to filter files really belonging to the project.
    val indexRoot = PathManager.getIndexRoot()
    try {
      FileUtil.delete(storedIndexDir)
      FileUtil.copyDir(indexRoot.toFile(), storedIndexDir.toFile())
      LOG.info("Stored index directory contains: ${dumpDirectory(storedIndexDir)}")
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }

    val contentDiagnostic = IndexContentDiagnosticDumper.getIndexContentDiagnosticForProject(project, indicator)
    IndexContentDiagnosticDumper.writeTo(getFileForDiagnostic(storedIndexDir), contentDiagnostic)
  }

  companion object {
    const val PREFIX = CMD_PREFIX + "storeIndices"

    private val LOG = logger<StoreIndices>()

    private fun getOrCreateDirectoryToStoreIndex(): Path {
      val property = System.getProperty("store.indices.command.stored.indexes.dump.directory")
      return if (property != null) {
        Paths.get(property)
      }
      else {
        try {
          FileUtil.createTempDirectory("stored_index", null).toPath()
        }
        catch (e: IOException) {
          throw RuntimeException(e)
        }
      }
    }

    fun getFileForDiagnostic(indexesHomeDirectory: Path): Path =
      indexesHomeDirectory.resolve("indexedFilePaths.json")

    fun dumpDirectory(dir: Path): List<String> {
      fun dumpFileInfo(file: Path): String {
        if (Files.isDirectory(file)) return file.fileName.toString()
        return "${file.fileName}(size=${Files.size(file)}b, mod stamp=${Files.getLastModifiedTime(file)})"
      }

      return Files.list(dir).use { it.map { p -> dumpFileInfo(p) }.toList() }
    }
  }
}