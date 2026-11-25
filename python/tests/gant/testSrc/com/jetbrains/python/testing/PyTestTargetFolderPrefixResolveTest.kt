// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.QualifiedName
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.extensions.ModuleBasedContextAnchor
import com.jetbrains.python.extensions.QNameResolveContext
import com.jetbrains.python.extensions.resolveToElement
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A test target whose module is named like a top-level folder: `tests.MyTestCase.test_method` for `tests/subdir/tests.py`.
 * The folder `tests` matches only a prefix of the name, so it must not be the resolved target.
 */
@Subsystems.TestRunner
@Layers.Functional
@TestFor(issues = ["PY-93409"])
class PyTestTargetFolderPrefixResolveTest : PyCodeInsightTestCase() {
  @Test
  fun `method in a module named like a top-level folder resolves to the method`() {
    createFiles("tests/subdir/tests.py" to TEST_MODULE)

    assertThat(resolve("tests.MyTestCase.test_method")).isEqualTo("function test_method in tests/subdir/tests.py")
  }

  @Test
  fun `method resolves when the folder is a package`() {
    createFiles("tests/__init__.py" to "", "tests/subdir/tests.py" to TEST_MODULE)

    assertThat(resolve("tests.MyTestCase.test_method")).isEqualTo("function test_method in tests/subdir/tests.py")
  }

  @Test
  fun `method resolves in the module that declares it, not in a module of the same name without it`() {
    createFiles("app_b/tests.py" to "class Other:\n    pass\n", "tests/subdir/tests.py" to TEST_MODULE)

    assertThat(resolve("tests.MyTestCase.test_method")).isEqualTo("function test_method in tests/subdir/tests.py")
  }

  @Test
  fun `folder is resolved when it is the whole name`() {
    createFiles("tests/subdir/tests.py" to TEST_MODULE)

    assertThat(resolve("tests")).isEqualTo("directory tests")
  }

  @Test
  fun `missing module in a folder does not resolve to the folder`() {
    createFiles("pkg/other.py" to "")

    assertThat(resolve("pkg.missing_module")).isEqualTo("nothing")
  }

  @Test
  fun `class in a module named like a top-level folder splits to that module`() {
    createFiles("tests/subdir/tests.py" to TEST_MODULE)

    val parts = runReadActionBlocking {
      QualifiedName.fromDottedString("tests.MyTestCase").tryResolveAndSplit(createContext())?.let {
        "${it.fileName} / ${it.elementName} in ${relativePath(it.file.virtualFile)}"
      }
    }

    assertThat(parts).isEqualTo("tests / MyTestCase in tests/subdir/tests.py")
  }

  @Test
  fun `class splits to the module under the folder, not to a module of the same name elsewhere`() {
    createFiles("app_b/tests.py" to TEST_MODULE, "tests/subdir/tests.py" to TEST_MODULE)

    val parts = runReadActionBlocking {
      QualifiedName.fromDottedString("tests.MyTestCase").tryResolveAndSplit(createContext())?.let {
        "${it.fileName} / ${it.elementName} in ${relativePath(it.file.virtualFile)}"
      }
    }

    assertThat(parts).isEqualTo("tests / MyTestCase in tests/subdir/tests.py")
  }

  private fun createFiles(vararg files: Pair<String, String>) {
    runInEdtAndWait {
      for ([path, text] in files) {
        VfsTestUtil.createFile(root, path, text)
      }
    }
    IndexingTestUtil.waitUntilIndexesAreReady(myFixture.project)
  }

  /** Resolves [name] as a test target of the working directory [root] does, and describes the element it gives. */
  private fun resolve(name: String): String = runReadActionBlocking {
    describe(QualifiedName.fromDottedString(name).resolveToElement(createContext()))
  }

  private fun createContext(): QNameResolveContext =
    QNameResolveContext(ModuleBasedContextAnchor(myFixture.module),
                        evalContext = TypeEvalContext.codeAnalysis(myFixture.project, null),
                        folderToStart = root,
                        allowInaccurateResult = true)

  private fun describe(element: PsiElement?): String = when (element) {
    null -> "nothing"
    is PsiDirectory -> "directory ${relativePath(element.virtualFile)}"
    is PyFunction -> "function ${element.name} in ${relativePath(element.containingFile.virtualFile)}"
    is PyClass -> "class ${element.name} in ${relativePath(element.containingFile.virtualFile)}"
    is PsiFile -> "file ${relativePath(element.virtualFile)}"
    else -> element.toString()
  }

  private fun relativePath(file: VirtualFile): String = VfsUtilCore.getRelativePath(file, root) ?: file.path

  private val root: VirtualFile
    get() = myFixture.tempDirFixture.getFile(".") ?: error("No source root")

  private companion object {
    val TEST_MODULE = """
      import unittest


      class MyTestCase(unittest.TestCase):
          def test_method(self):
              pass

          def test_other(self):
              pass
    """.trimIndent()
  }
}
