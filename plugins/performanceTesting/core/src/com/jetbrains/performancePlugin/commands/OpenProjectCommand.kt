package com.jetbrains.performancePlugin.commands

import com.intellij.diagnostic.hprof.action.SystemTempFilenameSupplier
import com.intellij.diagnostic.hprof.analysis.*
import com.intellij.diagnostic.hprof.util.AnalysisReport
import com.intellij.diagnostic.hprof.util.ListProvider
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.processPerProjectSupport
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

/**
 * Command to open a new project.
 *
 * To open a project in the current frame closing the current one:
 * Example: %openProject C:\Users\username\intellij
 *
 * To open a project in a new frame and don't close the current one:
 * Example: %openProject C:\Users\username\intellij false
 *
 * If you do the following:
 * %openProject /tmp/a false
 * %openProject /tmp/b false
 * %openProject /tmp/a false
 *
 * In the end, the same project a will be active and there will be 2 window frames.
 *
 * To perform Project Leak detection, pass the fourth argument as true but make sure that previous project is closed.
 * %openProject /tmp/a false true
 */
class OpenProjectCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "openProject"

    private val LOG = logger<OpenProjectCommand>()

    fun shouldOpenInSmartMode(project: Project): Boolean {
      return (!SystemProperties.getBooleanProperty("performance.execute.script.right.after.ide.opened", false)
              && !LightEdit.owns(project)
              && !SystemProperties.getBooleanProperty("performance.execute.script.after.scanning", false))
    }
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val arguments = text.split("\\s+".toRegex(), limit = 4).toTypedArray()
    val projectToOpen = if (arguments.size > 1) arguments[1] else ""
    val closeProjectBeforeOpening = arguments.size < 3 || arguments[2].toBoolean()
    val detectProjectLeak = arguments.size > 3 && arguments[3].toBoolean()
    if(!closeProjectBeforeOpening && detectProjectLeak) throw IllegalArgumentException("Previous project has to be closed to perform project leak detection")
    val project = context.project
    if (projectToOpen.isEmpty() && project.isDefault) {
      throw IllegalArgumentException("Path to project to open required")
    }

    if (!project.isDefault) {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          WindowManager.getInstance().updateDefaultFrameInfoOnProjectClose(project)

          // for backward compatibility with older code
          if (closeProjectBeforeOpening) {
            // prevent the script from stopping on project close
            context.setProject(null)

            ProjectManager.getInstance().closeAndDispose(project)
          }
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
      val projectStoreBaseDir = Path.of(projectPath)
      newProject = ProjectManagerEx.getInstanceEx().openProjectAsync(projectStoreBaseDir, OpenProjectTask(forceOpenInNewFrame = true))
      if (newProject == null) {
        // Don't stop if project was opened in a new instance
        if (!processPerProjectSupport().canBeOpenedInThisProcess(projectStoreBaseDir)) {
          return
        }

        throw IllegalStateException("Failed to open the project: $projectPath")
      }

      if (shouldOpenInSmartMode(newProject)) {
        val job = CompletableDeferred<Any?>()
        DumbService.getInstance(newProject).smartInvokeLater {
          job.complete(null)
        }
        job.join()
        if (detectProjectLeak) {
          analyzeSnapshot(newProject)
        }
      }
    }
    context.setProject(newProject)
  }

  /**
   * @param projectName project to exclude from analysis
   */
  private class AnalyzeProjectGraph(analysisContext: AnalysisContext, listProvider: ListProvider, val projectName: String)
    : AnalyzeGraph(analysisContext, listProvider) {
    override fun analyze(progress: ProgressIndicator): AnalysisReport = AnalysisReport().apply {
      traverseInstanceGraph(progress, this)

      val navigator = analysisContext.navigator
      for (l in 1..navigator.instanceCount) {
        val classDefinition = navigator.getClassForObjectId(l)
        if (classDefinition.name == ProjectImpl::class.java.name) {
          navigator.goTo(l)
          navigator.goToInstanceField(ProjectImpl::class.java.name, "cachedName")
          val projectUnderAnalysis = navigator.getStringInstanceFieldValue()
          if (projectUnderAnalysis != projectName) {
            LOG.info("Analyzing GC Root for $projectUnderAnalysis")
            val gcRootPathsTree = GCRootPathsTree(analysisContext, AnalysisConfig.TreeDisplayOptions.all(showSize = false), null)
            gcRootPathsTree.registerObject(l.toInt())
            mainReport.append(gcRootPathsTree.printTree())
          }
        }
      }
    }
  }

  /**
   * @param newProject current opened project, it will be excluded from analysis
   */
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