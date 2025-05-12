// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.diagnostic.hprof.action.SystemTempFilenameSupplier
import com.intellij.diagnostic.hprof.analysis.*
import com.intellij.diagnostic.hprof.util.AnalysisReport
import com.intellij.diagnostic.hprof.util.ListProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.NonNls
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.*

class DetectProjectLeaksCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "detectProjectLeaks"

    private val LOG = logger<DetectProjectLeaksCommand>()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val openProjectsNames: List<String> = ProjectManager.getInstance().openProjects.map { it.name }
    analyzeSnapshot(openProjectsNames)
  }

  private class AnalyzeProjectGraph(analysisContext: AnalysisContext, listProvider: ListProvider, val openProjectsNames: List<String>)
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
          if (!openProjectsNames.contains(projectUnderAnalysis)) {
            LOG.info("Analyzing GC Root for $projectUnderAnalysis")
            val gcRootPathsTree = GCRootPathsTree(analysisContext, AnalysisConfig.TreeDisplayOptions.all(showSize = false), null)
            gcRootPathsTree.registerObject(l.toInt())
            mainReport.append(gcRootPathsTree.printTree())
          }
        }
      }
    }
  }

  private fun analyzeSnapshot(openProjectsNames: List<String>) {
    val snapshotDate = SimpleDateFormat("dd.MM.yyyy_HH.mm.ss").format(Date())
    val snapshotFileName = "close-project-$snapshotDate.hprof"
    val snapshotPath = System.getProperty("memory.snapshots.path", SystemProperties.getUserHome()) + "/" + snapshotFileName

    MemoryDumpHelper.captureMemoryDump(snapshotPath)
    FileChannel.open(Paths.get(snapshotPath), StandardOpenOption.READ).use { channel ->
      val analysis = HProfAnalysis(channel, SystemTempFilenameSupplier()) { analysisContext, listProvider, progressIndicator ->
        AnalyzeProjectGraph(analysisContext, listProvider, openProjectsNames).analyze(progressIndicator).mainReport.toString()
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
