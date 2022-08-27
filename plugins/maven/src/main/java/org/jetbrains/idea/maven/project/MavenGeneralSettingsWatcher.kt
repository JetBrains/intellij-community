// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.changes.AsyncFilesChangesListener.Companion.subscribeOnVirtualFilesChanges
import com.intellij.openapi.externalSystem.autoimport.changes.FilesChangesListener
import com.intellij.openapi.externalSystem.autoimport.settings.ReadAsyncSupplier
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.idea.maven.buildtool.MavenImportSpec
import org.jetbrains.idea.maven.model.MavenTychoConstants
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import java.util.concurrent.ExecutorService

class MavenGeneralSettingsWatcher private constructor(
  private val manager: MavenProjectsManager,
  private val watcher: MavenProjectsManagerWatcher,
  backgroundExecutor: ExecutorService,
  parentDisposable: Disposable
) {

  private val generalSettings get() = manager.generalSettings
  private val embeddersManager get() = manager.embeddersManager

  private val settingsFiles: Set<String>
    get() = collectSettingsFiles().map { FileUtil.toCanonicalPath(it) }.toSet()

  private fun collectSettingsFiles() = sequence {
    generalSettings.effectiveUserSettingsIoFile?.path?.let { yield(it) }
    generalSettings.effectiveGlobalSettingsIoFile?.path?.let { yield(it) }
  }

  private fun fireSettingsChange() {
    embeddersManager.reset()
    MavenDistributionsCache.getInstance(manager.project).cleanCaches()
    watcher.scheduleUpdateAll(MavenImportSpec.IMPLICIT_IMPORT)
  }

  private fun fireSettingsXmlChange() {
    generalSettings.changed()
    // fireSettingsChange() will be called indirectly by pathsChanged listener on GeneralSettings object
  }

  init {
    generalSettings.addListener(
      {
        updateImportingSettings()
        fireSettingsChange()
      }, parentDisposable)
    val filesProvider = ReadAsyncSupplier.Builder(::settingsFiles)
      .coalesceBy(this)
      .build(backgroundExecutor)
    subscribeOnVirtualFilesChanges(false, filesProvider, object : FilesChangesListener {
      override fun apply() = fireSettingsXmlChange()
    }, parentDisposable)
  }

  private fun updateImportingSettings() {
    if (generalSettings.isTychoProject) {
      val importingSettings = manager.importingSettings
      val dependencyTypes = importingSettings.dependencyTypesAsSet
      dependencyTypes.add(MavenTychoConstants.PACKAGING_ECLIPSE_PLUGIN)
      dependencyTypes.add(MavenTychoConstants.PACKAGING_ECLIPSE_TEST_PLUGIN)
      importingSettings.dependencyTypes = dependencyTypes.joinToString(", ")
    }
  }

  companion object {
    @JvmStatic
    fun registerGeneralSettingsWatcher(
      manager: MavenProjectsManager,
      watcher: MavenProjectsManagerWatcher,
      backgroundExecutor: ExecutorService,
      parentDisposable: Disposable
    ) {
      MavenGeneralSettingsWatcher(manager, watcher, backgroundExecutor, parentDisposable)
    }
  }
}
