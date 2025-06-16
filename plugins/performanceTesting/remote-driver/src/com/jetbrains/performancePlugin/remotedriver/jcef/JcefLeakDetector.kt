package com.jetbrains.performancePlugin.remotedriver.jcef

import com.intellij.diagnostic.hprof.action.SystemTempFilenameSupplier
import com.intellij.diagnostic.hprof.analysis.*
import com.intellij.diagnostic.hprof.util.AnalysisReport
import com.intellij.diagnostic.hprof.util.ListProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption


@Suppress("unused")
internal class JcefLeakDetector {
  companion object {
    private val LOG = logger<JcefLeakDetector>()

    fun analyzeSnapshot(path: String): List<String> {

      val analysisResult = mutableListOf<String>()
      FileChannel.open(Paths.get(path), StandardOpenOption.READ).use { channel ->
        val analysis = HProfAnalysis(channel, SystemTempFilenameSupplier()) { analysisContext, listProvider, progressIndicator ->
          AnalyzeProjectGraph(analysisContext, listProvider).analyze(progressIndicator).mainReport.toString()
        }
        analysis.onlyStrongReferences = true
        analysis.includeClassesAsRoots = false
        analysis.setIncludeMetaInfo(false)
        analysisResult += analysis.analyze(ProgressManager.getGlobalProgressIndicator() ?: EmptyProgressIndicator())
        analysisResult.removeIf({it == ""})
        if (analysisResult.isNotEmpty()) {
          LOG.error("Snapshot analysis result: $analysisResult")
        }
      }
      return analysisResult
    }
  }


  private class AnalyzeProjectGraph(analysisContext: AnalysisContext, listProvider: ListProvider)
    : AnalyzeGraph(analysisContext, listProvider) {
    override fun analyze(progress: ProgressIndicator): AnalysisReport = AnalysisReport().apply {
      traverseInstanceGraph(progress, this)

      val navigator = analysisContext.navigator
      for (l in 1..navigator.instanceCount) {
        val classDefinition = navigator.getClassForObjectId(l)
        if (classDefinition.name == "org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel") {
          navigator.goTo(l)
          val gcRootPathsTree = GCRootPathsTree(analysisContext, AnalysisConfig.TreeDisplayOptions.all(showSize = false), null)
          gcRootPathsTree.registerObject(l.toInt())
          if (!gcRootPathsTree.printTree().contains("com.jetbrains.performancePlugin.remotedriver.jcef.JcefComponentWrapper"))
            mainReport.append(gcRootPathsTree.printTree())
        }
      }
    }
  }
}
