// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.AsyncFileChangeListenerBase
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class MavenGeneralSettingsWatcher private constructor(
  private val manager: MavenProjectsManager,
  private val watcher: MavenProjectsManagerWatcher,
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
    watcher.scheduleUpdateAll(true, false)
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
    val fileManager = VirtualFileManager.getInstance()
    fileManager.addAsyncFileListener(virtualFileSettingsListener, parentDisposable)
  }

  private inner class VirtualFileSettingsListener : AsyncFileChangeListenerBase() {
    private var hasRelevantChanges = false

    override fun init() {
      hasRelevantChanges = false
    }

    override fun isRelevant(path: String): Boolean {
      return path in settingsFiles
    }

    override fun updateFile(file: VirtualFile, event: VFileEvent) {
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
      parentDisposable: Disposable
    ) {
      MavenGeneralSettingsWatcher(manager, watcher, parentDisposable)
    }
  }
}
