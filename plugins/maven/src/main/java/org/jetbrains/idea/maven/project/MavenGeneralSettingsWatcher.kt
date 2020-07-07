// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ModificationType
import com.intellij.openapi.externalSystem.autoimport.changes.AsyncFilesChangesProviderImpl
import com.intellij.openapi.externalSystem.autoimport.changes.FilesChangesListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
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
    watcher.scheduleUpdateAll(true, true)
  }

  private fun fireSettingsXmlChange() {
    generalSettings.changed()
    // fireSettingsChange() will be called indirectly by pathsChanged listener on GeneralSettings object
  }

  init {
    val generalSettingsListener = MavenGeneralSettings.Listener { fireSettingsChange() }
    generalSettings.addListener(generalSettingsListener)
    Disposer.register(parentDisposable, Disposable { generalSettings.removeListener(generalSettingsListener) })

    val virtualFileSettingsListener = VirtualFileSettingsListener()
    AsyncFilesChangesProviderImpl(backgroundExecutor, ::settingsFiles)
      .subscribeAsAsyncVirtualFilesChangesProvider(false, virtualFileSettingsListener, parentDisposable)
  }

  private inner class VirtualFileSettingsListener : FilesChangesListener {
    private var hasRelevantChanges = false

    override fun init() {
      hasRelevantChanges = false
    }

    override fun onFileChange(path: String, modificationStamp: Long, modificationType: ModificationType) {
      hasRelevantChanges = true
    }

    override fun apply() {
      if (hasRelevantChanges) {
        fireSettingsXmlChange()
      }
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
