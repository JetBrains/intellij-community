package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.codeInsight.completion.*
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.ThreeState
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.completion.PythonLookupElement
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.PyTestFrameworkService
import com.jetbrains.python.testing.TestRunnerService
import com.jetbrains.python.testing.isTestElement
import icons.PythonIcons

private val pyTestName = PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.PY_TEST)

private object PyTestFixtureCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
    val pyFunction = PsiTreeUtil.getParentOfType(parameters.position, PyParameterList::class.java)?.containingFunction ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(pyFunction) ?: return
    val typeEvalContext = TypeEvalContext.userInitiated(pyFunction.project, pyFunction.containingFile)

    if (TestRunnerService.getInstance(module).projectConfiguration != pyTestName) {
      return
    }
    val usedParams = pyFunction.getParameters(typeEvalContext).mapNotNull { it.name }.toSet()
    if (isTestElement(pyFunction, ThreeState.UNSURE, typeEvalContext)) {
      getFixtures(module).map { it.name }.filter { !usedParams.contains(it) }.forEach {
        result.addElement(PythonLookupElement(it, false, PythonIcons.Python.Function))
      }
    }
  }
}

/**
 * Provide pytest fixtures are possible arguments for tests
 */
class PyTestFixtureCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement().inside(PyParameterList::class.java), PyTestFixtureCompletionProvider)
  }
}
