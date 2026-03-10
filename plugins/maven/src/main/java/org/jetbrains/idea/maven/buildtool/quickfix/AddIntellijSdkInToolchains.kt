// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenSettingsCache
import org.jetbrains.idea.maven.toolchains.ToolchainModel
import org.jetbrains.idea.maven.toolchains.ToolchainRequirement
import org.jetbrains.idea.maven.toolchains.addIntoToolchainsFile
import org.jetbrains.idea.maven.toolchains.findMatcher
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AddIntellijSdkInToolchains(val requirement: ToolchainRequirement, val syncAfterAdding: Boolean) : BuildIssueQuickFix {
  override val id: String = ID

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val result = CompletableFuture<Void>()
    val sdkPopupFactory = ApplicationManager.getApplication().service<SdkPopupFactory>()

    val sdks = findJdksInIdea(project, requirement)
    val popup = sdkPopupFactory.createBuilder()
      .withProject(project)
      .withSdkTypeFilter { it is JavaSdkType }
      .withSdkFilter { sdk ->
        sdks.any { it.homePath == sdk.homePath }
      }.onSdkSelected { sdk ->
        val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
        cs.launch(Dispatchers.IO) {
          waitForSdkToDownload(sdk)

          val path = MavenSettingsCache.getInstance(project).getEffectiveToolchainsFile()
          val toolchain = addIntoToolchainsFile(project, toolchains = path, sdk = sdk)
          if (toolchain != null) {
            val psiFile = readAction { toolchain.xmlTag?.containingFile }
            if (psiFile != null) {
              if (syncAfterAdding) {
                edtWriteAction { FileDocumentManager.getInstance().saveAllDocuments() }
                MavenProjectsManager.getInstance(project).updateAllMavenProjects(MavenSyncSpec.full("after adding toolchain requirement"))
              }
            }
          }
          result.complete(null)
        }
      }.onPopupClosed {
        result.complete(null)
      }.buildPopup()
    popup.showCenteredInCurrentWindow(project)
    return result
  }


  companion object {
    const val ID = "add_intellij_sdk_in_toolchains_qf"

    fun findJdksInIdea(project: Project, req: ToolchainRequirement?): List<Sdk> {
      if (req == null) return emptyList()
      if (req.type != "jdk") return emptyList()
      val projectJdkTable = ProjectJdkTable.getInstance()
      val sdkType = ExternalSystemJdkUtil.getJavaSdkType()
      return projectJdkTable.getSdksOfType(sdkType)
        .filterNotNull()
        .filter { it: Sdk -> JdkUtil.isCompatible(it, project) }
        .filter { it: Sdk -> it.homeDirectory?.toNioPath()?.let { JdkUtil.checkForJdk(it) } == true }
        .filter {
          val model = ToolchainModel.fromSdk(it)
          model != null && findMatcher("version").matches(
            "version",
            req.params["version"] ?: "",
            model
          )
        }
    }
  }
}


private suspend fun waitForSdkToDownload(sdk: Sdk) {
  val tracker = SdkDownloadTracker.getInstance()
  if (!tracker.isDownloading(sdk)) return

  val disposable = Disposer.newDisposable()
  val done = AtomicBoolean(false)
  fun resumeOnce(block: () -> Unit) {
    if (done.compareAndSet(false, true)) block()
  }

  withContext(Dispatchers.EDT) {
    suspendCancellableCoroutine { cont ->
      val registered = tracker.tryRegisterDownloadingListener(sdk, disposable, null) {
        resumeOnce { cont.resume(sdk) }
      }
      if (registered) {
        tracker.tryRegisterSdkDownloadFailureHandler(sdk) {
          resumeOnce { cont.resumeWithException(Exception("SDK download failed")) }
        }

        cont.invokeOnCancellation {
          Disposer.dispose(disposable)
        }
      }
      else {
        resumeOnce { cont.resume(sdk) }
      }
    }
  }
}