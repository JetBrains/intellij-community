package com.jetbrains.performancePlugin.commands

import com.intellij.diagnostic.hprof.action.SystemTempFilenameSupplier
import com.intellij.diagnostic.hprof.analysis.*
import com.intellij.diagnostic.hprof.util.AnalysisReport
import com.intellij.diagnostic.hprof.util.ListProvider
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.util.BitUtil
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.awt.Frame
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.*

class OpenProjectCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "openProject"

    private val LOG = Logger.getInstance(OpenProjectCommand::class.java)

    fun shouldOpenInSmartMode(project: Project): Boolean {
      return (!SystemProperties.getBooleanProperty("performance.execute.script.after.project.opened", false)
              && !LightEdit.owns(project)
              && !SystemProperties.getBooleanProperty("performance.execute.script.after.scanning", false))
    }
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val arguments = text.split("\\s+".toRegex(), limit = 4).toTypedArray()
    val projectToOpen = if (arguments.size > 1) arguments[1] else ""
    val closeProjectBeforeOpening = arguments.size < 3 || arguments[2].toBoolean()
    val detectProjectLeak = arguments.size > 3 && arguments[3].toBoolean()
    val project = context.project
    if (projectToOpen.isEmpty() && project.isDefault) {
      throw IllegalArgumentException("Path to project to open required")
    }

    if (!project.isDefault) {
      withContext(Dispatchers.EDT) {
        WindowManager.getInstance().updateDefaultFrameInfoOnProjectClose(project)

        // prevent the script from stopping on project close
        context.setProject(null)

        // for backward compatibility with older code
        if (closeProjectBeforeOpening) {
          ProjectManager.getInstance().closeAndDispose(project)
        }
      }
      RecentProjectsManager.getInstance().updateLastProjectPath()
      WelcomeFrame.showIfNoProjectOpened()
    }
    val projectPath = projectToOpen.ifEmpty { project.basePath!! }
    var newProject = ProjectManagerEx.getOpenProjects().find { it.basePath == projectPath }
    if (newProject != null) {
      val projectFrame = WindowManager.getInstance().getFrame(newProject) ?: return
      val frameState = projectFrame.extendedState
      if (BitUtil.isSet(frameState, Frame.ICONIFIED)) {
        projectFrame.extendedState = BitUtil.set(frameState, Frame.ICONIFIED, false)
      }
      projectFrame.toFront()
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
        val mostRecentFocusOwner = projectFrame.mostRecentFocusOwner
        if (mostRecentFocusOwner != null) {
          IdeFocusManager.getGlobalInstance().requestFocus(mostRecentFocusOwner, true)
        }
      }
    }
    else {
      newProject = ProjectManagerEx.getInstanceEx().openProjectAsync(Path.of(projectPath), OpenProjectTask(forceOpenInNewFrame = true))
                   ?: throw IllegalStateException("Failed to open the project: $projectPath")
      if (shouldOpenInSmartMode(newProject)) {
        val job = CompletableDeferred<Any?>()
        DumbService.getInstance(newProject).smartInvokeLater {
          if (detectProjectLeak) {
            analyzeSnapshot(newProject)
          }
          job.complete(null)
        }
        job.join()
      }
    }
    context.setProject(newProject)
  }

  private class AnalyzeProjectGraph(analysisContext: AnalysisContext, listProvider: ListProvider, val projectName: String)
    : AnalyzeGraph(analysisContext, listProvider) {
    override fun analyze(progress: ProgressIndicator): AnalysisReport = AnalysisReport().apply {
      traverseInstanceGraph(progress, this)

      val navigator = analysisContext.navigator
      for (l in 1..navigator.instanceCount) {
        val classDefinition = navigator.getClassForObjectId(l)
        if (classDefinition.name != ProjectImpl::class.java.name) {
          navigator.goTo(l)
          navigator.goToInstanceField(ProjectImpl::class.java.name, "cachedName")
          if (navigator.getStringInstanceFieldValue() == projectName) {
            val gcRootPathsTree = GCRootPathsTree(analysisContext, AnalysisConfig.TreeDisplayOptions.all(showSize = false), null)
            gcRootPathsTree.registerObject(l.toInt())
            mainReport.append(gcRootPathsTree.printTree())
          }
        }
      }
    }
  }

  private fun analyzeSnapshot(newProject: Project) {
    val snapshotDate = SimpleDateFormat("dd.MM.yyyy_HH.mm.ss").format(Date())
    val snapshotFileName = "reopen-project-$snapshotDate.hprof"
    val snapshotPath = System.getProperty("memory.snapshots.path", SystemProperties.getUserHome()) + "/" + snapshotFileName

    MemoryDumpHelper.captureMemoryDump(snapshotPath)
    FileChannel.open(Paths.get(snapshotPath), StandardOpenOption.READ).use { channel ->
      val analysis = HProfAnalysis(channel, SystemTempFilenameSupplier()) { analysisContext, listProvider, progressIndicator ->
        AnalyzeProjectGraph(analysisContext, listProvider, newProject.name).analyze(progressIndicator).mainReport.toString()
      }
      analysis.onlyStrongReferences = true
      analysis.includeClassesAsRoots = false
      analysis.setIncludeMetaInfo(false)
      val analysisResult = analysis.analyze(ProgressManager.getGlobalProgressIndicator() ?: EmptyProgressIndicator())
      if (analysisResult.isNotEmpty()) {
        LOG.error("Snapshot analysis result: $analysisResult")
      }
    }
  }
}
