package org.jetbrains.settingsRepository.git

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.ShutDownTracker
import org.eclipse.jgit.lib.Repository

class GitBareRepositoryManager(private val repository: Repository) {
  init {
    if (ApplicationManager.getApplication()?.isUnitTestMode() != true) {
      ShutDownTracker.getInstance().registerShutdownTask(object: Runnable {
        override fun run() {
          repository.close()
        }
      })
    }
  }
}