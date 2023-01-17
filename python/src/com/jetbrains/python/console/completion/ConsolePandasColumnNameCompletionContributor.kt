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
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.python.actions.checkIfAvailableAndShowHint
import com.jetbrains.python.actions.getCustomDescriptor
import com.jetbrains.python.actions.getSelectedPythonConsole
import com.jetbrains.python.console.PyConsoleOptions
import com.jetbrains.python.console.PyExecuteConsoleCustomizer
import com.jetbrains.python.console.PydevConsoleCommunication
import com.jetbrains.python.console.PythonConsoleView
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.lang.invoke.MethodHandles
import java.util.concurrent.Callable

class ConsolePandasColumnNameCompletionContributor : CompletionContributor(), DumbAware {

  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = parameters.editor.project ?: return
        val editor = parameters.editor
        val virtualFile = parameters.originalFile.virtualFile
        if (virtualFile.fileType.defaultExtension != "py") return

        if (!checkIfAvailableAndShowHint(editor)) return
        val existingConsole = getDescriptorIfExist(virtualFile, project, editor) ?: return
        val pydevRunner: PythonConsoleView = existingConsole.executionConsole as? PythonConsoleView ?: return
        val consoleCommunication = pydevRunner.executeActionHandler?.consoleCommunication as? PydevConsoleCommunication ?: return
        if (consoleCommunication.isExecuting) return

        val dataFrameCandidates = getCompleteAttribute(parameters)
        ApplicationUtil.runWithCheckCanceled(Callable {
          result.addAllElements(dataFrameCandidates.flatMap { candidate ->
            val columnsDataFrame = getDataFrameColumns(project, candidate.psiName, consoleCommunication)
            processDataFrameColumns(candidate.psiName, columnsDataFrame, candidate.needValidatorCheck,
                                                                       parameters.position, project, false)
          })
        }, ProgressManager.getInstance().progressIndicator)
      }
    })
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


  private fun getDataFrameColumns(project: Project, name: @NlsSafe String, consoleCommunication: PydevConsoleCommunication): List<String> {
    val service = project.service<ConsolePandasColumnNameRetrievalService>()
    return service.getPandasColumns(consoleCommunication, name)
  }

  companion object {
    val LOG = Logger.getInstance(MethodHandles.lookup().lookupClass())
    const val COMPLETION_LOG_MESSAGE = "Incorrectly created python script with expression: "
  }

}

interface ConsolePandasColumnNameRetrievalService {
  fun getPandasColumns(consoleCommunication: PydevConsoleCommunication, name: @NlsSafe String): List<String>
}

class DummyConsolePandasColumnNameRetrievalService : ConsolePandasColumnNameRetrievalService {
  override fun getPandasColumns(consoleCommunication: PydevConsoleCommunication, name: @NlsSafe String): List<String> = listOf()
}


class ConsolePandasColumnNameRetrievalServiceImpl(val project: Project) : ConsolePandasColumnNameRetrievalService {
  override fun getPandasColumns(consoleCommunication: PydevConsoleCommunication, name: @NlsSafe String): List<String> {
    if (!PyConsoleOptions.getInstance(project).isAutoCompletionEnabled) {
      return emptyList()
    }
    val debugValue = consoleCommunication.evaluate(PANDAS_COLUMN_NAMES_CODE.format(name, name), true, true)
    return when (debugValue.type) {
      "str" -> debugValue.value?.let { parseDebugValue(it) } ?: emptyList()
      "NameError" -> emptyList()
      "SyntaxError" -> {
        ConsolePandasColumnNameCompletionContributor.LOG.info(ConsolePandasColumnNameCompletionContributor.COMPLETION_LOG_MESSAGE + name)
        emptyList()
      }
      else -> emptyList()
    }
  }


  private fun parseDebugValue(value: String): List<String>? {

    try {
      val map = Json.decodeFromString<Map<String, List<String>>>(value)
      return map["columns"]
    }
    catch (_: SerializationException) {
      return emptyList()
    }
    catch (_: IllegalArgumentException) {
      return emptyList()
    }
  }

  companion object {
    private const val PANDAS_COLUMN_NAMES_CODE = "__import__('json').dumps({\"columns\":list(%s.columns.get_level_values(0))}) if str(%s.__class__)==\"<class 'pandas.core.frame.DataFrame'>\" else {}"
  }
}