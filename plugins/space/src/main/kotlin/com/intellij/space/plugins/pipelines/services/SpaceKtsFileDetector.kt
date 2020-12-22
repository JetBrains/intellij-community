// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services

import com.intellij.space.plugins.pipelines.utils.DslFileFinder
import com.intellij.space.tools.spaceKtsToolwindow
import com.intellij.space.utils.LifetimedDisposable
import com.intellij.space.utils.LifetimedDisposableImpl
import com.intellij.space.vcs.PostStartupActivity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.*
import libraries.klogging.logger
import runtime.reactive.Property
import runtime.reactive.mutableProperty

private val log = logger<SpaceKtsFileDetector>()

class SpaceKtsFileDetectorActivator : PostStartupActivity() {
  override fun runActivity(project: Project) {
    project.service<SpaceKtsFileDetector>()
  }
}

// listens to file system and exposes script dsl file
@Service
class SpaceKtsFileDetector(val project: Project) : LifetimedDisposable by LifetimedDisposableImpl() {

  val dslFile: Property<VirtualFile?> get() = _dslFile

  private val _dslFile = mutableProperty<VirtualFile?>(null)

  private val fileListener = object : VirtualFileListener {
    override fun propertyChanged(event: VirtualFilePropertyEvent) {
      if (event.propertyName == "name") {
        handleFileChanged(event.oldValue.toString())
        handleFileChanged(event.newValue.toString())
      }
    }

    override fun fileCreated(event: VirtualFileEvent) {
      handleFileChanged(event.fileName)
    }

    override fun fileDeleted(event: VirtualFileEvent) {
      handleFileChanged(event.fileName)
    }

    override fun fileMoved(event: VirtualFileMoveEvent) {
      handleFileChanged(event.fileName)
    }
  }

  init {
    log.info("SpaceKtsFileDetector")

    refreshScript()

    LocalFileSystem.getInstance().addVirtualFileListener(fileListener)

    lifetime.add {
      LocalFileSystem.getInstance().removeVirtualFileListener(fileListener)
    }

    _dslFile.forEach(lifetime) { file ->
      project.spaceKtsToolwindow?.setAvailable(file != null, null)
    }
  }


  fun refreshScript() {
    runReadAction {
      if (!project.isDisposed) {
        _dslFile.value = checkIsDslFileExists()
      }
    }
  }

  fun handleFileChanged(name: String) {
    if (DslFileFinder.checkFileNameIsApplicable(name)) {
      refreshScript()
    }
  }

  private fun checkIsDslFileExists(): VirtualFile? {
    return DslFileFinder.find(project)
  }

}
