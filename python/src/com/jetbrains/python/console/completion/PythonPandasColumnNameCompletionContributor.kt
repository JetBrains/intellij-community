package com.jetbrains.python.console.completion

import com.intellij.codeInsight.completion.*
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.intellij.xdebugger.frame.XValueChildrenList
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.actions.checkIfAvailableAndShowHint
import com.jetbrains.python.actions.getCustomDescriptor
import com.jetbrains.python.actions.getSelectedPythonConsole
import com.jetbrains.python.console.PyConsoleOptions
import com.jetbrains.python.console.PyExecuteConsoleCustomizer
import com.jetbrains.python.console.PydevConsoleCommunication
import com.jetbrains.python.console.PythonConsoleView
import com.jetbrains.python.debugger.PyFrameAccessor
import com.jetbrains.python.debugger.PyFrameListener
import com.jetbrains.python.debugger.values.DataFrameDebugValue
import com.jetbrains.python.psi.PyPlainStringElement
import java.lang.invoke.MethodHandles
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

class PythonPandasColumnNameCompletionContributor : CompletionContributor(), DumbAware {

  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = parameters.editor.project ?: return

        val service = project.service<PandasColumnNameRetrievalService>()
        if (!service.canComplete(parameters)) return
        val dataFrameObjects = service.getInformationFromRuntime(parameters) ?: return

        val dataFrameCandidates = getCompleteAttribute(parameters)

        ApplicationUtil.runWithCheckCanceled(Callable {
          result.addAllElements(dataFrameCandidates.flatMap { candidate ->
            val columnsDataFrame = service.getPandasColumns(dataFrameObjects, candidate)
            processDataFrameColumns(candidate.psiName,
                                    columnsDataFrame,
                                    candidate.needValidatorCheck,
                                    parameters.position,
                                    project,
                                    true)
          })
        }, ProgressManager.getInstance().progressIndicator)
      }
    })
  }

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    super.fillCompletionVariants(parameters, createCustomMatcher(parameters, result))
  }

  private fun createCustomMatcher(parameters: CompletionParameters, result: CompletionResultSet): CompletionResultSet {
    if (parameters.position is PyPlainStringElement) {
      val newPrefix = parameters.position.text.substring(1, parameters.offset - parameters.position.textRange.startOffset)
      return result.withPrefixMatcher(PlainPrefixMatcher(newPrefix))
    }
    return result
  }

  companion object {
    val LOG = Logger.getInstance(MethodHandles.lookup().lookupClass())

    val consoleEnv: ConcurrentHashMap<PydevConsoleCommunication, XValueChildrenList?> = ConcurrentHashMap()

    val consoleListener = ConsoleFrameListener()

    fun getConsoleXValueChildrenList(project: Project, virtualFile: VirtualFile, editor: Editor): XValueChildrenList? {
      val existingConsole = getDescriptorIfExist(virtualFile, project, editor) ?: return null
      val pydevRunner: PythonConsoleView = existingConsole.executionConsole as? PythonConsoleView ?: return null
      val consoleCommunication = pydevRunner.executeActionHandler?.consoleCommunication as? PydevConsoleCommunication ?: return null
      if (consoleCommunication.isExecuting) return null
      return consoleEnv[consoleCommunication]
    }

    class ConsoleFrameListener : PyFrameListener {
      override fun frameChanged(): Unit = Unit

      override fun sessionStopped(communication: PyFrameAccessor?) {
        if (communication is PydevConsoleCommunication) {
          consoleEnv.remove(communication)
        }
      }

      override fun updateVariables(communication: PyFrameAccessor?, values: XValueChildrenList?) {
        if (communication is PydevConsoleCommunication) {
          consoleEnv[communication] = values
        }
      }
    }

    private fun getDescriptorIfExist(virtualFile: VirtualFile?, project: Project, editor: Editor): RunContentDescriptor? {
      if (virtualFile != null && PyExecuteConsoleCustomizer.instance.isCustomDescriptorSupported(virtualFile)) {
        tryGetCustomDescriptor(project, editor)?.let { return it }
      }
      return getSelectedPythonConsole(project)
    }

    private fun tryGetCustomDescriptor(project: Project, editor: Editor): RunContentDescriptor? {
      try {
        val (descriptor, _) = getCustomDescriptor(project, editor)
        return descriptor
      }
      catch (e: IllegalStateException) {
        return null
      }
    }
  }
}

class DummyConsolePandasColumnNameRetrievalService : PandasColumnNameRetrievalService {
  override fun canComplete(parameters: CompletionParameters): Boolean = true

  override fun getInformationFromRuntime(parameters: CompletionParameters): Map<String, DataFrameDebugValue> = emptyMap()

  override fun getPandasColumns(dataFrameObjects: Map<String, DataFrameDebugValue>,
                                candidate: PandasDataFrameCandidate): Set<String> = emptySet()

}


class PyPandasColumnNameRetrievalService(val project: Project) : PandasColumnNameRetrievalService {

  override fun canComplete(parameters: CompletionParameters): Boolean {
    if (parameters.originalFile.virtualFile.fileType !is PythonFileType) return false

    if (!PyConsoleOptions.getInstance(project).isAutoCompletionEnabled) return false

    val virtualFile = parameters.originalFile.virtualFile
    if (virtualFile.fileType !is PythonFileType) return false

    val editor = parameters.editor
    if (!checkIfAvailableAndShowHint(editor)) return false
    return true
  }

  override fun getInformationFromRuntime(parameters: CompletionParameters): Map<String, DataFrameDebugValue>? {
    if (parameters.originalFile.virtualFile.fileType !is PythonFileType) return emptyMap()

    val virtualFile = parameters.originalFile.virtualFile
    val editor = parameters.editor
    val valuesEnv = PythonPandasColumnNameCompletionContributor.getConsoleXValueChildrenList(project, virtualFile, editor)
                    ?: return emptyMap()
    return selectDataFrameDebugValue(valuesEnv)
  }
}