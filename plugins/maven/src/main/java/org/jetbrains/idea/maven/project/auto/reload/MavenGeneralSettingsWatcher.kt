// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.Stamp
import com.intellij.openapi.externalSystem.autoimport.changes.AsyncFileChangesListener.Companion.subscribeOnVirtualFilesChanges
import com.intellij.openapi.externalSystem.autoimport.changes.FilesChangesListener
import com.intellij.openapi.externalSystem.autoimport.settings.AsyncSupplier
import com.intellij.openapi.externalSystem.autoimport.settings.BackgroundAsyncSupplier
import com.intellij.openapi.util.io.toCanonicalPath
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.concurrent.ExecutorService

@ApiStatus.Internal
class MavenGeneralSettingsWatcher(
  private val manager: MavenProjectsManager,
  private val backgroundExecutor: ExecutorService
) {

  private val generalSettings get() = manager.generalSettings
  private val embeddersManager get() = manager.embeddersManager

  private fun collectSettingsFiles(): Set<String> {
    val result = LinkedHashSet<String>()
    val userSettingsFile = MavenUtil.resolveUserSettingsPath(generalSettings.userSettingsFile, null)
    result.add(userSettingsFile.toCanonicalPath())
    return result
  }

  private fun fireSettingsChange() {
    embeddersManager.reset()
    val project = manager.project
    MavenDistributionsCache.getInstance(project).cleanCaches()
    manager.scheduleUpdateAllMavenProjects(MavenSyncSpec.full("MavenGeneralSettingsWatcher.fireSettingsChange"))
  }

  private fun fireSettingsXmlChange() {
    generalSettings.changed()
    // fireSettingsChange() will be called indirectly by pathsChanged listener on GeneralSettings object
  }

  fun subscribeOnSettingsChanges(parentDisposable: Disposable) {
    generalSettings.addListener(::fireSettingsChange, parentDisposable)
  }

  fun subscribeOnSettingsFileChanges(parentDisposable: Disposable) {
    val filesProvider = BackgroundAsyncSupplier(
      manager.project,
      supplier = AsyncSupplier.blocking(::collectSettingsFiles),
      shouldKeepTasksAsynchronous = { AutoImportProjectTracker.isAsyncChangesProcessing },
      backgroundExecutor = backgroundExecutor,
    )
    subscribeOnVirtualFilesChanges(false, filesProvider, object : FilesChangesListener {
      override fun onFileChange(stamp: Stamp, path: String, modificationStamp: Long, modificationType: ExternalSystemModificationType) {
        val fileChangeMessage = "File change: $path, $modificationStamp, $modificationType"
        MavenLog.LOG.debug(fileChangeMessage)
      }
      override fun apply() = fireSettingsXmlChange()
    }, parentDisposable)
  }
}
