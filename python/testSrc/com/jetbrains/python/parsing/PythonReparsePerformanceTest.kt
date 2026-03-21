// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.PsiTestUtil
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.jetbrains.python.fixtures.PyTestCase

/**
 * Measures incremental vs full reparse performance for Python statement lists.
 *
 * Generates a large Python file (~12 000 lines) and performs 50 small edits
 * scattered across different function bodies, simulating real typing.
 */
@PerformanceUnitTest
class PythonReparsePerformanceTest : PyTestCase() {

  fun testIncrementalReparsePerformance() = withRegistryValue(true) {
    doManyEditsAndMeasure("incremental reparse (ON)")
  }

  fun testFullReparsePerformance() = withRegistryValue(false) {
    doManyEditsAndMeasure("full reparse (OFF)", fileName = "large_module_full.py")
  }

  private fun doManyEditsAndMeasure(benchmarkName: String, fileName: String = "large_module.py") {
    val source = generateLargePythonFile()
    val file = myFixture.addFileToProject(fileName, source)
    file.node.firstChildNode // force initial parse

    val pdm = PsiDocumentManager.getInstance(myFixture.project)
    val document = pdm.getDocument(file)!!

    val targets = Array(50) { c -> "        x_5 = 5 + $c + 5" }

    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      Benchmark.newBenchmark(benchmarkName) {
        for (i in 0 until 50) {
          val offset = document.text.indexOf(targets[i])
          if (offset < 0) continue
          document.insertString(offset, "        edit_$i = $i\n")
          pdm.commitDocument(document)
        }
      }.start()

      PsiTestUtil.checkFileStructure(file)
    }
  }

  private fun withRegistryValue(value: Boolean, action: () -> Unit) {
    val original = Registry.`is`(REGISTRY_KEY)
    try {
      Registry.get(REGISTRY_KEY).setValue(value)
      action()
    }
    finally {
      Registry.get(REGISTRY_KEY).setValue(original)
    }
  }

  companion object {
    private const val REGISTRY_KEY = "python.statement.lists.incremental.reparse"

    /** 50 classes x 20 methods x 10 statements ≈ 10 000 statements ≈ 12 000 lines. */
    private fun generateLargePythonFile(): String = buildString(500_000) {
      for (c in 0 until 50) {
        append("class MyClass$c:\n")
        for (m in 0 until 20) {
          append("    def method_${c}_$m(self):\n")
          for (s in 0 until 10) {
            append("        x_$s = $s + $c + $m\n")
          }
          append("        return x_0\n\n")
        }
        append("\n")
      }
    }
  }
}
