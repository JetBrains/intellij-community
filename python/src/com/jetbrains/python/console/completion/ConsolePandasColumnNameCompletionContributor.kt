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
import com.jetbrains.python.debugger.values.completePandasDataFrameColumns
import java.lang.invoke.MethodHandles
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

class ConsolePandasColumnNameCompletionContributor : CompletionContributor(), DumbAware {

  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = parameters.editor.project ?: return
        val editor = parameters.editor
        val virtualFile = parameters.originalFile.virtualFile
        if (virtualFile.fileType.defaultExtension != "py") return

        if (!checkIfAvailableAndShowHint(editor)) return

        val service = project.service<ConsolePandasColumnNameRetrievalService>()
        val dataFrameObjects = service.getInformationFromRuntime(virtualFile, editor) ?: return
        val dataFrameCandidates = getCompleteAttribute(parameters)

        ApplicationUtil.runWithCheckCanceled(Callable {
          result.addAllElements(dataFrameCandidates.flatMap { candidate ->
            val columnsDataFrame = service.getPandasColumns(dataFrameObjects, candidate)
            processDataFrameColumns(candidate.psiName, columnsDataFrame, candidate.needValidatorCheck,
                                    parameters.position, project, false)
          })
        }, ProgressManager.getInstance().progressIndicator)
      }
    })
  }

  companion object {
    val LOG = Logger.getInstance(MethodHandles.lookup().lookupClass())

    internal val consoleEnv: ConcurrentHashMap<PydevConsoleCommunication, XValueChildrenList?> = ConcurrentHashMap()

    val consoleListener = ConsoleFrameListener()

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
  }
}

interface ConsolePandasColumnNameRetrievalService {
  fun getInformationFromRuntime(virtualFile: VirtualFile, editor: Editor): Map<String, DataFrameDebugValue>?
  fun getPandasColumns(dataFrameObjects: Map<String, DataFrameDebugValue>, candidate: PandasDataFrameCandidate): Set<String>
}

class DummyConsolePandasColumnNameRetrievalService : ConsolePandasColumnNameRetrievalService {
  override fun getInformationFromRuntime(virtualFile: VirtualFile, editor: Editor): Map<String, DataFrameDebugValue> = emptyMap()
  override fun getPandasColumns(dataFrameObjects: Map<String, DataFrameDebugValue>,
                                candidate: PandasDataFrameCandidate): Set<String> = emptySet()

}


class ConsolePandasColumnNameRetrievalServiceImpl(val project: Project) : ConsolePandasColumnNameRetrievalService {

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

  override fun getInformationFromRuntime(virtualFile: VirtualFile, editor: Editor): Map<String, DataFrameDebugValue>? {
    if (!PyConsoleOptions.getInstance(project).isAutoCompletionEnabled) {
      return emptyMap()
    }
    val existingConsole = getDescriptorIfExist(virtualFile, project, editor) ?: return emptyMap()
    val pydevRunner: PythonConsoleView = existingConsole.executionConsole as? PythonConsoleView ?: return emptyMap()
    val consoleCommunication = pydevRunner.executeActionHandler?.consoleCommunication as? PydevConsoleCommunication ?: return emptyMap()
    if (consoleCommunication.isExecuting) return emptyMap()
    val valuesEnv = ConsolePandasColumnNameCompletionContributor.consoleEnv[consoleCommunication] ?: return emptyMap()
    val dataFrameObjects = mutableMapOf<String, DataFrameDebugValue>()
    for (elem in 0 until valuesEnv.size()) {
      val currentValue = valuesEnv.getValue(elem)
      if (currentValue is DataFrameDebugValue) {
        dataFrameObjects[currentValue.name] = currentValue
      }
    }
    if (dataFrameObjects.isEmpty()) return null
    return dataFrameObjects
  }

  override fun getPandasColumns(dataFrameObjects: Map<String, DataFrameDebugValue>, candidate: PandasDataFrameCandidate): Set<String> {
    return dataFrameObjects[candidate.psiName]?.let { completePandasDataFrameColumns(it.treeColumns, candidate.columnsBefore) }
           ?: emptySet()
  }
}