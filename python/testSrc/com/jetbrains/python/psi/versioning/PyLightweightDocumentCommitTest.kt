// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.versioning

import com.intellij.idea.TestFor
import com.intellij.platform.testFramework.junit5.codeInsight.psi.LightweightCommitScenario
import com.intellij.platform.testFramework.junit5.codeInsight.psi.assertLightweightCommitScenario
import com.intellij.platform.testFramework.junit5.codeInsight.psi.replaceBetween
import com.intellij.platform.testFramework.junit5.codeInsight.psi.runVersionedTest
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.junit5.framework.pyMockSdkFixture
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDocStringOwner
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * AI-generated tests.
 *
 * Checks the behavior of lightweight commit across various Python language structures.
 */
@TestApplication
@TestFor(classes = [PsiDocumentManagerBase::class])
@Layers.Functional
@Subsystems.CodeInsight
class PyLightweightDocumentCommitTest {

  private companion object {
    private const val PYTHON_TRIPLE_QUOTE = "\"\"\""

    private val BASE_TEXT: String = $$"""
      $${PYTHON_TRIPLE_QUOTE}Stress fixture for lightweight document commit.

      The source intentionally mixes Python language constructs which produce different Python PSI nodes:
      docstrings, plain and from imports, decorators, dataclasses, nested classes, async generators,
      comprehensions, generator expressions, match statements, PEP 695 generics, f-strings, type aliases,
      lambdas, with statements and try/except/finally blocks.
      $$PYTHON_TRIPLE_QUOTE

      import asyncio
      import dataclasses
      from collections.abc import Callable, Iterable, Iterator
      from contextlib import suppress
      from dataclasses import dataclass, field
      from enum import Enum
      from typing import Any

      __all__ = ["FeatureStress", "Mode", "Point", "Renderer", "describe_all", "make_summary"]

      type Renderer[T] = Callable[[T], str]

      DEFAULTS: dict[str, list[float]] = {
          "integers": [1.0, 2.0, 3.0],
          "mixed": [1.0, 2.5],
      }


      class Mode(Enum):
          $${PYTHON_TRIPLE_QUOTE}Enumeration used to exercise class level entries.$${PYTHON_TRIPLE_QUOTE}

          FAST = "fast"
          # LEGACY_ENTRIES_START
          SAFE = "safe"
          LEGACY = "legacy"
          # LEGACY_ENTRIES_END

          def label(self) -> str:
              # `value` here is the enum payload, not an attribute of the fixture
              return self.value.upper()


      @dataclass(frozen=True)
      class Point:
          $${PYTHON_TRIPLE_QUOTE}Dataclass with defaults and a field factory.$${PYTHON_TRIPLE_QUOTE}

          x: float = 0.0
          y: float = 0.0
          tags: list[str] = field(default_factory=list)

          def shifted(self, dx: float) -> "Point":
              return dataclasses.replace(self, x=self.x + dx)


      def memoize[F: Callable[..., Any]](func: F) -> F:
          $${PYTHON_TRIPLE_QUOTE}Decorator exercising PEP 695 generics over a closure.$${PYTHON_TRIPLE_QUOTE}
          cache: dict[tuple[Any, ...], Any] = {}

          def wrapper(*args: Any) -> Any:
              if args not in cache:
                  cache[args] = func(*args)
              return cache[args]

          return wrapper


      class FeatureStress[T]:
          $${PYTHON_TRIPLE_QUOTE}Fixture class with methods, a property and a nested class.

          :param value: the value exposed through :meth:`__iter__`
          $$PYTHON_TRIPLE_QUOTE

          default_name = "default"

          def __init__(self, value: T) -> None:
              self.value = value
              self.names: list[str] = []

          # MEMBERS_START

          def describe(self, item: object) -> str:
              $${PYTHON_TRIPLE_QUOTE}Describe an arbitrary input with a match statement.

              :param item: anything accepted by the fixture
              :return: a stable textual representation
              $$PYTHON_TRIPLE_QUOTE
              # DESCRIBE_BODY_START
              match item:
                  case None:
                      return "null"
                  case str() as text if text.strip():
                      return text.strip()
                  case int() | float() as number:
                      return f"number:{number}"
                  case Mode() as mode:
                      return f"mode:{mode.label()}"
                  case [first, *rest]:
                      return f"list:{first}/{len(rest)}"
                  case {"kind": kind}:
                      return f"mapping:{kind}"
                  case _:
                      return str(item)
              # DESCRIBE_BODY_END

          @property
          def title(self) -> str:
              $${PYTHON_TRIPLE_QUOTE}Property built from an f-string with nested quotes.$${PYTHON_TRIPLE_QUOTE}
              return f"{self.default_name}:{DEFAULTS['integers'][0]}"

          async def collect(self, sources: Iterable[str]) -> list[str]:
              collected = [source async for source in self.stream(sources)]
              await asyncio.sleep(0)
              return collected

          async def stream(self, sources: Iterable[str]):
              for source in sources:
                  yield source

          def summarize(self, values: Iterable[object]) -> str:
              rendered = (str(value) for value in values if value is not None)
              return "|".join(sorted(rendered, key=lambda item: (len(item), item)))

          def risky(self, mode: Mode) -> str:
              try:
                  with suppress(ValueError):
                      return mode.label()
                  return self.default_name
              except AttributeError as ex:
                  raise RuntimeError("unexpected mode") from ex
              finally:
                  self.names.append(f"closed:{mode!r}")

          def __iter__(self) -> Iterator[T]:
              return iter([self.value])

          # LEGACY_NESTED_START
          class Registry:
              $${PYTHON_TRIPLE_QUOTE}Nested class holding the known names.$${PYTHON_TRIPLE_QUOTE}

              known: set[str] = {"default"}

              @classmethod
              def register(cls, name: str) -> None:
                  cls.known.add(name)

          # LEGACY_NESTED_END

          # MEMBERS_END


      @memoize
      def make_summary(*items: object) -> str:
          $${PYTHON_TRIPLE_QUOTE}Top level documented function using a list comprehension.$${PYTHON_TRIPLE_QUOTE}
          return ", ".join([str(item) for item in items])


      def describe_all(fixture: FeatureStress[Any], items: Iterable[object]) -> list[str]:
          # a plain comprehension over the public API of the fixture
          return [fixture.describe(item) for item in items]


      # LEGACY_START
      def legacy_adapter(key: str, *values: str) -> dict[str, list[str]]:
          $${PYTHON_TRIPLE_QUOTE}Legacy helper dropped by the removal scenario.$${PYTHON_TRIPLE_QUOTE}
          return {key: list(values)}
      # LEGACY_END
    """.trimIndent()

    private val REPLACED_DESCRIBE_BODY = """
      computed = "fallback"
      if item is None:
          computed = "replacement:null"
      elif isinstance(item, Mode):
          computed = f"mode:{item.label()}"
      elif isinstance(item, (list, tuple)):
          computed = f"sequence:{len(item)}"
      elif (text := str(item).strip()):
          computed = text
      return computed
    """.trimIndent().withIndent("        ") + "\n"

    private val INSERTED_MEMBER = "\n" + $$"""
      @staticmethod
      def inserted_feature[R](sink: list[R], value: R) -> R:
          $${PYTHON_TRIPLE_QUOTE}Added by the lightweight commit test to cover generic methods.

          :param sink: accepts the same value through a mutable list
          :param value: value to store and return
          :return: the value visible only in the lightweight committed PSI branch
          $$PYTHON_TRIPLE_QUOTE
          sink.append(value)
          return value
    """.trimIndent().withIndent("    ") + "\n"

    private fun String.withIndent(indent: String): String =
      lineSequence().joinToString("\n") { if (it.isEmpty()) it else indent + it }

    private fun replaceDescribeBody(): String = BASE_TEXT.replaceBetween(
      "        # DESCRIBE_BODY_START\n",
      "        # DESCRIBE_BODY_END",
      REPLACED_DESCRIBE_BODY,
    )

    private fun insertMember(text: String): String = text.replace(
      "    # MEMBERS_START\n",
      "    # MEMBERS_START\n$INSERTED_MEMBER",
    )

    private fun removeLegacyDeclarations(text: String): String = text
      .replaceBetween(
        "    # LEGACY_ENTRIES_START\n",
        "    # LEGACY_ENTRIES_END",
        "    SAFE = \"safe\"\n",
      )
      .replaceBetween(
        "    # LEGACY_NESTED_START\n",
        "    # LEGACY_NESTED_END",
        "",
      )
      .replaceBetween(
        "# LEGACY_START\n",
        "# LEGACY_END",
        "",
      )

    /**
     * Adapts the file-level assertions expected by the shared harness to the assertions below, which are stated against
     * the single `FeatureStress` class and the file that contains it.
     */
    private fun pyScenario(
      name: String,
      updatedText: String,
      assertScenarioPsi: (PyFile, PyClass) -> Unit,
    ): LightweightCommitScenario<PyFile> = LightweightCommitScenario(name, updatedText) { pyFile ->
      assertScenarioPsi(pyFile, requireNotNull(pyFile.findTopLevelClass("FeatureStress")))
    }

    private val replaceBodyScenario = pyScenario(
      name = "replace complex match method body",
      updatedText = replaceDescribeBody(),
      assertScenarioPsi = { _, mainClass ->
        val describe = requireNotNull(mainClass.findMethodByName("describe", false, null))
        Assertions.assertTrue(describe.text.contains("elif isinstance(item, Mode):"), describe.text)
        Assertions.assertTrue(describe.text.contains("return computed"), describe.text)
        Assertions.assertFalse(describe.text.contains("match item:"), describe.text)
        Assertions.assertEquals(4, describe.statementList.statements.size, describe.text)
      },
    )

    private val insertMemberScenario = pyScenario(
      name = "insert docstring decorator and generic member",
      updatedText = insertMember(BASE_TEXT),
      assertScenarioPsi = { _, mainClass ->
        val inserted = mainClass.findMethodByName("inserted_feature", false, null)
        Assertions.assertNotNull(inserted, "Inserted generic method should be available in lightweight committed PSI")
        Assertions.assertTrue(inserted!!.docStringExpression!!.text.contains("through a mutable list"))
        Assertions.assertEquals("R", inserted.typeParameterList!!.typeParameters.single().name)
        Assertions.assertEquals(listOf("staticmethod"), inserted.decoratorList!!.decorators.map { it.name })
      },
    )

    private val removeDeclarationsScenario = pyScenario(
      name = "remove class level entry and nested declaration",
      updatedText = removeLegacyDeclarations(BASE_TEXT),
      assertScenarioPsi = { pyFile, mainClass ->
        val mode = requireNotNull(pyFile.findTopLevelClass("Mode"))
        Assertions.assertEquals(listOf("FAST", "SAFE"), mode.classAttributes.map { it.name }, mode.text)
        Assertions.assertNull(mainClass.findNestedClass("Registry", false), mainClass.text)
        Assertions.assertNull(pyFile.findTopLevelFunction("legacy_adapter"))
      },
    )

    private val wideUpdateScenario = pyScenario(
      name = "wide update with replacements insertions and removals",
      updatedText = removeLegacyDeclarations(insertMember(replaceDescribeBody())),
      assertScenarioPsi = { pyFile, mainClass ->
        Assertions.assertNotNull(mainClass.findMethodByName("inserted_feature", false, null))
        val describe = requireNotNull(mainClass.findMethodByName("describe", false, null))
        Assertions.assertTrue(describe.text.contains("elif isinstance(item, (list, tuple)):"), describe.text)
        Assertions.assertNull(mainClass.findNestedClass("Registry", false), mainClass.text)
        Assertions.assertNull(pyFile.findTopLevelFunction("legacy_adapter"))
      },
    )

    private fun assertPyShape(pyFile: PyFile) {
      Assertions.assertTrue(pyFile.docStringExpression!!.text.contains("Stress fixture for lightweight document commit"))
      Assertions.assertEquals(listOf("Mode", "Point", "FeatureStress"), pyFile.topLevelClasses.map { it.name })
      Assertions.assertEquals(
        listOf("Mode", "Point", "FeatureStress"),
        pyFile.statements.filterIsInstance<PyClass>().map { it.name },
      )
      Assertions.assertEquals(
        listOf("memoize", "make_summary", "describe_all"),
        pyFile.topLevelFunctions.map { it.name }.filterNot { it == "legacy_adapter" },
      )
      Assertions.assertEquals(
        listOf("collections.abc", "contextlib", "dataclasses", "enum", "typing"),
        pyFile.fromImports.map { it.importSourceQName?.toString() },
      )
      Assertions.assertEquals(
        listOf("FeatureStress", "Mode", "Point", "Renderer", "describe_all", "make_summary"),
        pyFile.dunderAll,
      )
      Assertions.assertEquals(listOf("Renderer"), pyFile.typeAliasStatements.map { it.name })

      val mainClass = requireNotNull(pyFile.findTopLevelClass("FeatureStress"))
      Assertions.assertNotNull(mainClass.findClassAttribute("default_name", false, null))
      Assertions.assertNotNull(mainClass.findMethodByName("describe", false, null))
      Assertions.assertNotNull(mainClass.findMethodByName("title", false, null))
      Assertions.assertNotNull(mainClass.findMethodByName("collect", false, null))
      Assertions.assertNotNull(mainClass.findMethodByName("summarize", false, null))

      val documented = PsiTreeUtil.findChildrenOfType(pyFile, PyDocStringOwner::class.java)
        .count { it.docStringExpression != null }
      Assertions.assertTrue(documented >= 7, "Expected the docstring owners to survive the reparse, got $documented")
    }
  }

  private val tempDir = tempPathFixture()
  private val _project = projectFixture(tempDir, openAfterCreation = true)
  private val _module = _project.pyModuleFixture(tempDir, addPathToSourceRoot = true)

  @Suppress("unused")
  private val _mockSdk = _project.pyMockSdkFixture(_module) { PythonMockSdk.create(LanguageLevel.getLatest()) }
  private val _sourceRoot = _module.sourceRootFixture()
  private val _psiFile = _sourceRoot.psiFileFixture("feature_stress.py", "\n")
  private val _editor = _psiFile.editorFixture()

  private val project by _project
  private val editor by _editor

  @BeforeEach
  fun forceLatestLanguageLevel() {
    PythonLanguageLevelPusher.setForcedLanguageLevel(project, LanguageLevel.getLatest())
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  @AfterEach
  fun restoreLanguageLevel() {
    PythonLanguageLevelPusher.setForcedLanguageLevel(project, null)
  }

  /**
   * Every scenario gets its own project, because repeated lightweight commits of the same document corrupt the
   * versioned tree -- see [repeated lightweight commits of one document keep the tree consistent].
   */
  private fun runScenario(scenario: LightweightCommitScenario<PyFile>) {
    runVersionedTest(project) {
      assertLightweightCommitScenario(project, editor.document, BASE_TEXT, scenario, ::assertPyShape)
    }
  }

  /** AI-generated test. */
  @Test
  fun `lightweight commit replaces a complex match method body`() = runScenario(replaceBodyScenario)

  /** AI-generated test. */
  @Test
  fun `lightweight commit inserts a documented generic member`() = runScenario(insertMemberScenario)

  /** AI-generated test. */
  @Test
  fun `lightweight commit removes a class level entry and a nested declaration`() = runScenario(removeDeclarationsScenario)

  /** AI-generated test. */
  @Test
  fun `lightweight commit applies replacements insertions and removals at once`() = runScenario(wideUpdateScenario)

  /**
   * AI-generated test.
   *
   * Lightweight-commits one document several times in a row, which is what a caller that reparses on every keystroke
   * would do.
   *
   * Was not Python-specific: the same defect showed up in `KotlinLightweightDocumentCommitTest` and
   * `GoLightweightDocumentCommitTest`, on the third commit there.
   */
  @Test
  fun `repeated lightweight commits of one document keep the tree consistent`() {
    runVersionedTest(project) {
      repeat(4) {
        assertLightweightCommitScenario(project, editor.document, BASE_TEXT, replaceBodyScenario, ::assertPyShape)
      }
    }
  }
}
