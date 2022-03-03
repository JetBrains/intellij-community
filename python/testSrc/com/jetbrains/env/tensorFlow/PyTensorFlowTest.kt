// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.tensorFlow

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.testFramework.UsefulTestCase
import com.jetbrains.env.EnvTestTagsRequired
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.PyExecutionFixtureTestTask
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.tools.sdkTools.SdkCreationType
import junit.framework.TestCase
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

class PyTensorFlowTest : PyEnvTestCase() {

  @Test
  @EnvTestTagsRequired(tags = ["tensorflow1", "tensorflow_oldpaths"])
  fun tensorFlow1ModulesOldPaths() {
    runPythonTest(TensorFlowModulesTask("tf1old.txt", setOf("lite")))
  }

  @Test
  @EnvTestTagsRequired(tags = ["tensorflow1", "tensorflow_newpaths"])
  fun tensorFlow1ModulesNewPaths() {
    runPythonTest(TensorFlowModulesTask("tf1new.txt"))
  }

  @Test
  @EnvTestTagsRequired(tags = ["tensorflow2", "tensorflow_newpaths"])
  fun tensorFlow2ModulesNewPaths() {
    runPythonTest(TensorFlowModulesTask("tf2new.txt"))
  }

  private class TensorFlowModulesTask(private val expectedModulesFile: String,
                                      private val modulesToIgnoreInResolve: Set<String> = emptySet()) : PyExecutionFixtureTestTask(null) {

    override fun runTestOn(sdkHome: String, existingSdk: Sdk?) {
      val actualModules = loadActualModules(createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_AND_SKELETONS))

      val expectedModules = loadModules(
        Files.readAllLines(Paths.get(PythonTestUtil.getTestDataPath(), "packages", "tensorflow", expectedModulesFile))
      )
      TestCase.assertEquals(expectedModules, actualModules)

      runCompletion(actualModules.keys)
      runResolve(actualModules)
    }

    private fun loadActualModules(sdk: Sdk): Map<String, String> {
      val script = Paths.get(PythonTestUtil.getTestDataPath(), "packages", "tensorflow", "modules.py").toAbsolutePath().toString()
      val env = PySdkUtil.activateVirtualEnv(sdk)
      val timeout = TimeUnit.SECONDS.toMillis(30).toInt()

      val commandLine = GeneralCommandLine(sdk.homePath).withParameters(script).withEnvironment(env)
      return loadModules(CapturingProcessHandler(commandLine).runProcess(timeout, true).stdoutLines)
    }

    private fun loadModules(lines: List<String>): Map<String, String> {
      return lines
        .asSequence()
        .map { it.split(' ', limit = 2) }
        .map { it[0] to it[1] }
        .toMap()
    }

    private fun runCompletion(modules: Collection<String>) {
      // `from tensorflow.<m>` completes
      // `import tensorflow.<m>` completes
      // `tensorflow.<m>` completes
      configureAndCompleteAtCaret("from tensorflow.<caret>", modules)
      configureAndCompleteAtCaret("import tensorflow.<caret>", modules)
      configureAndCompleteAtCaret("import tensorflow\ntensorflow.<caret>", modules)
    }

    private fun runResolve(modules: Map<String, String>) {
      // `from tensorflow.<m>` resolves
      // `import tensorflow.<m>` resolves
      // `tensorflow.<m>` resolves
      // everything resolves to the same element

      modules.asSequence().filter { (module, _) -> module !in modulesToIgnoreInResolve }.forEach { (module, path) ->
        val first = configureAndResolveAtCaret("from tensorflow.$module<caret> import *")
        val second = configureAndResolveAtCaret("import tensorflow.$module<caret>")
        val third = configureAndResolveAtCaret("import tensorflow\ntensorflow.$module<caret>")

        UsefulTestCase.assertSame("first: ${moduleToPath(first)} vs second: ${moduleToPath(second)}", first, second)
        UsefulTestCase.assertSame("first: ${moduleToPath(first)} vs third: ${moduleToPath(third)}", first, third)
        TestCase.assertEquals(path, moduleToPath(first))
      }
    }

    private fun configureAndCompleteAtCaret(text: String, modules: Collection<String>) {
      myFixture.configureByText(PythonFileType.INSTANCE, text)
      myFixture.completeBasic()
      UsefulTestCase.assertContainsElements(myFixture.lookupElementStrings!!, modules)
    }

    private fun configureAndResolveAtCaret(text: String): PsiElement {
      val file = myFixture.configureByText(PythonFileType.INSTANCE, text)
      return ApplicationManager.getApplication().runReadAction(
        Computable {
          val reference = myFixture.file.findElementAt(myFixture.caretOffset - 1)!!.parent as PyReferenceExpression
          val resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeAnalysis(project, file))
          PyUtil.turnDirIntoInit(reference.followAssignmentsChain(resolveContext).element)!!
        }
      )
    }

    private fun moduleToPath(module: PsiElement): String {
      return ApplicationManager.getApplication().runReadAction(
        Computable {
          var current = (module as PyFile).containingDirectory
          val components = mutableListOf<String>()
          while (current.name != "site-packages") {
            components.add(current.name)
            current = current.parent
          }
          components.asReversed().joinToString(".")
        }
      )
    }
  }
}
