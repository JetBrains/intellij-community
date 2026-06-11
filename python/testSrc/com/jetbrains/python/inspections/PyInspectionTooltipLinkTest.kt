// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf

/**
 * Verifies that inspection tooltips render type and symbol names as highlighted, navigable links
 * (the `#element/<fqn>` format understood by the editor tooltip link handler), the same way Quick
 * Documentation renders them. The Problems-view description stays plain text (golden inspection tests
 * keep matching); only the editor-hover tooltip is enriched.
 *
 * @see PyInspectionMessages.CodifiedParam
 */
@TestFor(classes = [PyInspectionMessages::class], issues = ["PY-90264"])
class PyInspectionTooltipLinkTest : PyCodeInsightTestCase() {

  /** Per-test disposable; resets any registry override the test made (the fixture is shared across the class). */
  private lateinit var flagDisposable: Disposable

  @BeforeEach
  fun setUpFlagDisposable() {
    flagDisposable = Disposer.newDisposable("PyInspectionTooltipLinkTest flag")
  }

  @AfterEach
  fun tearDownFlagDisposable() {
    Disposer.dispose(flagDisposable)
  }

  @Test
  fun `annotated assignment type mismatch has clickable type links`() {
    val info = highlight<PyTypeCheckerInspection>("x: int = [1, 2]", "Expected type")
    assertEquals("Expected type 'int', got 'list[Literal[1, 2]]' instead", info.description)
    // builtins are linked (and resolve) by their qualified name `builtins.<name>` (PY-87879), and keep
    // their highlight color, exactly as Quick Documentation renders them
    assertLink(info, "builtins.int")
    assertLink(info, "builtins.list")
    assertTrue("<span style=\"color:" in info.toolTip!!, info.toolTip!!)
    assertResolves("builtins.int")
    assertResolves("builtins.list")
  }

  // PY-90264: the numeric tower expands a `float` annotation to a `float | int` union; both members must still
  // render as highlighted, navigable links (re-parsing the name `float` yields the union, not a bare class).
  @Test
  fun `numeric tower union members are clickable`() {
    val info = highlight<PyTypeCheckerInspection>("_: float = \"\"", "Expected type")
    assertEquals("Expected type 'float | int', got 'Literal[\"\"]' instead", info.description)
    assertLink(info, "builtins.float")
    assertLink(info, "builtins.int")
    assertResolves("builtins.float")
    assertResolves("builtins.int")
  }

  @Test
  fun `call argument type mismatch has clickable type links`() {
    val info = highlight<PyTypeCheckerInspection>(
      """
      def f(value: int): pass
      f([1, 2])
      """.trimIndent(),
      "Expected type"
    )
    assertEquals("Expected type 'int', got 'list[Literal[1, 2]]' instead", info.description)
    assertLink(info, "builtins.int")
    assertLink(info, "builtins.list")
  }

  @Test
  fun `method signature mismatch has clickable symbol links`() {
    val info = highlight<PyMethodOverridingInspection>(
      """
      class A:
          def f(self, x: int): ...
      class B(A):
          def f(self, x: str): ...
      """.trimIndent(),
      "Signature of method"
    )
    assertEquals("Signature of method 'B.f()' does not match signature of the base method in class 'A'", info.description)

    // the overriding method B.f and the base class A are both clickable in the tooltip (the fqn carries the
    // temp module prefix, e.g. #element/<module>.B.f, so match on the trailing name + link text)
    val tooltip = info.toolTip!!
    assertTrue(Regex("""<a href="#element/[\w.]*B\.f">B\.f\(\)</a>""").containsMatchIn(tooltip), tooltip)
    assertTrue(Regex("""<a href="#element/[\w.]*\bA">A</a>""").containsMatchIn(tooltip), tooltip)

    val linkTargets = Regex("""#element/([\w.]+)""").findAll(tooltip).map { it.groupValues[1] }.toList()
    assertInstanceOf<PyFunction>(runReadActionBlocking { QualifiedNameProviderUtil.qualifiedNameToElement(linkTargets.single { it.endsWith("B.f") }, myFixture.project) })
    assertInstanceOf<PyClass>(runReadActionBlocking { QualifiedNameProviderUtil.qualifiedNameToElement(linkTargets.single { it.endsWith(".A") }, myFixture.project) })
  }

  @Test
  fun `abstract class must implement links class without quoting description`() {
    val info = highlight<PyAbstractClassInspection>(
      """
      import abc
      class Foo(abc.ABC):
          @abc.abstractmethod
          def m(self): ...
      class Bar(Foo): ...
      """.trimIndent(),
      "Class"
    )
    // the template has a bare {0} (no quotes), so the description must stay unquoted...
    assertEquals("Class Bar must implement all abstract methods", info.description)
    // ...while the tooltip still links the class name
    assertTrue(Regex("""<a href="#element/[\w.]*\bBar">Bar</a>""").containsMatchIn(info.toolTip!!), info.toolTip!!)
  }

  // a plain unresolved reference must show single quotes in the Problems-view description
  // and a <code> span in the editor tooltip — never a literal backtick from the bundle template.
  @Test
  @TestFor(issues = ["PY-90264"])
  fun `unresolved reference quotes description and codes tooltip`() {
    val info = highlight<PyUnresolvedReferencesInspection>("foo", "Unresolved reference")
    assertEquals("Unresolved reference 'foo'", info.description)
    assertFalse("`" in info.description, info.description)
    assertTrue("<code>foo</code>" in info.toolTip!!, info.toolTip!!)
  }

  // PY-80221: the type-mismatch breakdown shown below the headline in the tooltip renders its type references
  // as navigable links too, not just the headline. Here `int`/`str` appear only in the nested breakdown
  // ("`str` is not assignable to `int`"), so finding and resolving their links proves the breakdown is enriched.
  @Test
  @TestFor(issues = ["PY-80221"])
  fun `type mismatch breakdown renders clickable type links`() {
    val info = highlight<PyTypeCheckerInspection>(
      """
      from typing import Protocol
      class A(Protocol):
          a: int
      class C:
          a: str
      x: A = C()
      """.trimIndent(),
      "Expected type"
    )
    assertTrue("incompatible with protocol" in info.toolTip!!, info.toolTip!!)
    assertLink(info, "builtins.int")
    assertLink(info, "builtins.str")
    assertResolves("builtins.int")
    assertResolves("builtins.str")
  }

  // PY-80221: the invariance breakdown links the owner class (e.g. `Box`), which resolves. The type variable
  // itself stays a plain <code> span, since the tooltip link handler resolves only classes and functions.
  @Test
  @TestFor(issues = ["PY-80221"])
  fun `invariant type parameter breakdown links the owner class`() {
    Registry.get("python.subtypechecks.respect.variance").setValue(true, flagDisposable)
    val info = highlight<PyTypeCheckerInspection>(
      """
      from typing import Generic, TypeVar
      T = TypeVar("T")
      class Box(Generic[T]):
          def __init__(self, x: T) -> None:
              self.x = x
      bad = Box(True)
      b: Box[int] = bad
      """.trimIndent(),
      "Expected type"
    )
    val tooltip = info.toolTip!!
    assertTrue("invariant" in tooltip, tooltip)
    assertTrue("<code>T</code>" in tooltip, tooltip)
    val linkTargets = Regex("""#element/([\w.]+)""").findAll(tooltip).map { it.groupValues[1] }.toList()
    assertInstanceOf<PyClass>(runReadActionBlocking { QualifiedNameProviderUtil.qualifiedNameToElement(linkTargets.first { it.endsWith(".Box") }, myFixture.project) })
  }

  private fun assertLink(info: HighlightInfo, name: String) {
    assertTrue("""href="#element/$name"""" in info.toolTip!!, info.toolTip!!)
  }

  /** The `#element/<fqn>` link target must resolve, so clicking it in the tooltip navigates. */
  private fun assertResolves(fqn: String) {
    // resolution queries the stub index, which requires a read action (the test body itself runs without one)
    assertInstanceOf<PyClass>(runReadActionBlocking { QualifiedNameProviderUtil.qualifiedNameToElement(fqn, myFixture.project) })
  }

  private inline fun <reified T: LocalInspectionTool> highlight(text: String, descriptionPrefix: String): HighlightInfo {
    myFixture.configureByText(PythonFileType.INSTANCE, text)
    myFixture.enableInspections(T::class.java)
    return myFixture.doHighlighting().single { it.description?.startsWith(descriptionPrefix) == true }
  }
}
