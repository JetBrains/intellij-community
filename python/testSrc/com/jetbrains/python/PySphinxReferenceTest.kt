// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.documentation.docstrings.SphinxReferences
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction

private const val TQ = "\"\"\""

/**
 * Tests for Sphinx Python-domain cross-references inside docstrings and line comments.
 */
@Subsystems.CodeInsight
@Layers.Functional
@TestFor(issues = ["PY-27635"])
class PySphinxReferenceTest : PyTestCase() {

  fun testResolveClassRole() {
    configure(
      """
      class MyClass:
          pass

      def f():
          $TQ:py:class:`MyClass`$TQ
      """
    )
    assertResolvesTo("MyClass`", PyClass::class.java, "MyClass")
  }

  fun testResolveFunctionRole() {
    configure(
      """
      def my_function():
          pass

      def f():
          $TQ:py:func:`my_function`$TQ
      """
    )
    assertResolvesTo("my_function`", PyFunction::class.java, "my_function")
  }

  fun testResolveQualifiedMethodRole() {
    configure(
      """
      class MyClass:
          def my_method(self):
              pass

      def f():
          $TQ:py:meth:`MyClass.my_method`$TQ
      """
    )
    // Each dotted component is its own reference.
    assertResolvesTo("MyClass.my_method`", PyClass::class.java, "MyClass")
    assertResolvesTo("my_method`", PyFunction::class.java, "my_method")
  }

  fun testResolveShortRole() {
    configure(
      """
      class MyClass:
          pass

      def f():
          $TQ:class:`MyClass`$TQ
      """
    )
    assertResolvesTo("MyClass`", PyClass::class.java, "MyClass")
  }

  fun testResolveTildePrefixedRole() {
    configure(
      """
      class MyClass:
          def my_method(self):
              pass

      def f():
          $TQ:py:meth:`~MyClass.my_method`$TQ
      """
    )
    assertResolvesTo("my_method`", PyFunction::class.java, "my_method")
  }

  fun testResolveExplicitTitleRole() {
    configure(
      """
      class MyClass:
          pass

      def f():
          $TQ:py:class:`the class <MyClass>`$TQ
      """
    )
    assertResolvesTo("MyClass>", PyClass::class.java, "MyClass")
  }

  fun testSuppressedRoleHasNoReference() {
    configure(
      """
      class MyClass:
          pass

      def f():
          $TQ:py:class:`!MyClass`$TQ
      """
    )
    val offset = myFixture.file.text.lastIndexOf("MyClass`")
    assertNull(myFixture.file.findReferenceAt(offset))
  }

  fun testFindUsagesIncludesDocstringRole() {
    configure(
      """
      class MyClass:
          pass

      def f():
          $TQ:py:class:`MyClass`$TQ
      """
    )
    val cls = (myFixture.file as PyFile).findTopLevelClass("MyClass")!!
    val usages = myFixture.findUsages(cls)
    assertEquals(1, usages.size)
  }

  fun testRenameClassUpdatesDocstringRole() {
    configure(
      """
      class MyClass:
          pass

      def f():
          $TQ:py:class:`MyClass`$TQ
      """
    )
    val cls = (myFixture.file as PyFile).findTopLevelClass("MyClass")!!
    myFixture.renameElement(cls, "Renamed")
    assertTrue(myFixture.file.text, myFixture.file.text.contains(":py:class:`Renamed`"))
  }

  fun testRenameFromDocstringRole() {
    configure(
      """
      def my_function():
          pass

      def f():
          $TQ:py:func:`my_function`$TQ
      """
    )
    val offset = myFixture.file.text.lastIndexOf("my_function`")
    val element = myFixture.file.findReferenceAt(offset)!!.resolve()!!
    myFixture.renameElement(element, "renamed_function")
    assertTrue(myFixture.file.text, myFixture.file.text.contains(":py:func:`renamed_function`"))
    assertTrue(myFixture.file.text, myFixture.file.text.contains("def renamed_function():"))
  }

  fun testResolveRoleInLineComment() {
    configure(
      """
      class MyClass:
          pass

      # see :py:class:`MyClass`
      x = 1
      """
    )
    assertResolvesTo("MyClass`", PyClass::class.java, "MyClass")
  }

  fun testRenameClassUpdatesLineComment() {
    configure(
      """
      class MyClass:
          pass

      # see :py:class:`MyClass`
      x = 1
      """
    )
    val cls = (myFixture.file as PyFile).findTopLevelClass("MyClass")!!
    myFixture.renameElement(cls, "Renamed")
    assertTrue(myFixture.file.text, myFixture.file.text.contains("# see :py:class:`Renamed`"))
  }

  fun testTagRangesCoverMarkersOnly() {
    configure(
      """
      def f():
          $TQ See :py:class:`Connection` and :py:meth:`Connection.close`.

          .. py:function:: connect
          $TQ
      """
    )
    val docstring = (myFixture.file as PyFile).findTopLevelFunction("f")!!.docStringExpression!!
    val text = docstring.text
    val markers = SphinxReferences.findTagRanges(docstring).map { text.substring(it.startOffset, it.endOffset) }
    assertSameElements(markers, ":py:class:", ":py:meth:", ".. py:function::")
  }

  fun testRoleInAnInjectedCodeBlockIsNotAReference() {
    configure(
      """
      class MyClass:
          pass

      def f():
          $TQ
          .. code-block:: python

             # uses :py:class:`MyClass`
          $TQ
      """
    )
    val offset = myFixture.file.text.lastIndexOf("MyClass`")
    assertNull(myFixture.file.findReferenceAt(offset))
    assertNull(findInjectedReferenceAt(offset))
  }

  @TestFor(issues = ["PY-62267"])
  fun testRoleInADocstringOfAnInjectedCodeBlockIsNotAReference() {
    configure(
      """
      class MyClass:
          pass

      def f():
          $TQ
          .. code-block:: python

             def g():
                 '''Uses :py:class:`MyClass`.'''
          $TQ
      """
    )
    val offset = myFixture.file.text.lastIndexOf("MyClass`")
    assertNull(myFixture.file.findReferenceAt(offset))
    assertNull(findInjectedReferenceAt(offset))
  }

  fun testResolveRoleInTheSecondPartOfAConcatenatedDocstring() {
    configure(
      """
      class MyClass:
          pass

      def f():
          ${TQ}first part $TQ ${TQ}see :py:class:`MyClass`$TQ
      """
    )
    assertResolvesTo("MyClass`", PyClass::class.java, "MyClass")
  }

  fun testNonPythonDomainRoleIsNotAReference() {
    configure(
      """
      def f():
          $TQ:ref:`connection-guide` and :doc:`/topics/pooling`$TQ
      """
    )
    assertNull(myFixture.file.findReferenceAt(myFixture.file.text.indexOf("connection-guide")))
    assertNull(myFixture.file.findReferenceAt(myFixture.file.text.indexOf("topics")))
  }

  /** A module rename carries the file extension, which must not reach the qualified name. */
  fun testRenameModuleUpdatesRoleWithoutTheFileExtension() {
    val module = addPyFile(
      "mod.py",
      """
      class Target:
          pass
      """
    )
    configure(
      """
      import mod

      def f():
          $TQ:py:class:`mod.Target`$TQ
      """
    )
    myFixture.renameElement(module, "renamed.py")
    assertTrue(myFixture.file.text, myFixture.file.text.contains(":py:class:`renamed.Target`"))
  }

  fun testBindToElementRewritesTheWholeTargetPath() {
    addPyFile("pkg/__init__.py", "")
    val module = addPyFile(
      "pkg/mod.py",
      """
      class Target:
          pass
      """
    )
    configure(
      """
      def f():
          $TQ:py:class:`Target`$TQ
      """
    )
    bindReferenceAt("Target`", module.findTopLevelClass("Target")!!)
    assertTrue(myFixture.file.text, myFixture.file.text.contains(":py:class:`pkg.mod.Target`"))
  }

  fun testBindToElementUsesTheCanonicalImportPath() {
    addPyFile(
      "pkg/__init__.py",
      """
      from pkg._impl import Target
      """
    )
    val module = addPyFile(
      "pkg/_impl.py",
      """
      class Target:
          pass
      """
    )
    configure(
      """
      def f():
          $TQ:py:class:`Target`$TQ
      """
    )
    bindReferenceAt("Target`", module.findTopLevelClass("Target")!!)
    assertTrue(myFixture.file.text, myFixture.file.text.contains(":py:class:`pkg.Target`"))
  }

  fun testBindToElementKeepsTheMemberPathInsideTheCanonicalImportPath() {
    addPyFile(
      "pkg/__init__.py",
      """
      from pkg._impl import Target
      """
    )
    val module = addPyFile(
      "pkg/_impl.py",
      """
      class Target:
          def close(self):
              pass
      """
    )
    configure(
      """
      def f():
          $TQ:py:meth:`close`$TQ
      """
    )
    bindReferenceAt("close`", module.findTopLevelClass("Target")!!.findMethodByName("close", false, null)!!)
    assertTrue(myFixture.file.text, myFixture.file.text.contains(":py:meth:`pkg.Target.close`"))
  }

  fun testBindToElementKeepsTheNestedClassPathInsideTheCanonicalImportPath() {
    addPyFile(
      "pkg/__init__.py",
      """
      from pkg._impl import Outer
      """
    )
    val module = addPyFile(
      "pkg/_impl.py",
      """
      class Outer:
          class Inner:
              def close(self):
                  pass
      """
    )
    configure(
      """
      def f():
          $TQ:py:meth:`close`$TQ
      """
    )
    val inner = module.findTopLevelClass("Outer")!!.findNestedClass("Inner", false)!!
    bindReferenceAt("close`", inner.findMethodByName("close", false, null)!!)
    assertTrue(myFixture.file.text, myFixture.file.text.contains(":py:meth:`pkg.Outer.Inner.close`"))
  }

  /** A class that a function defines has no import path, so the markup stays as it is. */
  fun testBindToElementKeepsTheTargetOfALocalClass() {
    val module = addPyFile(
      "pkg/_impl.py",
      """
      def factory():
          class Local:
              pass
          return Local
      """
    )
    configure(
      """
      def f():
          $TQ:py:class:`Local`$TQ
      """
    )
    val local = PsiTreeUtil.findChildOfType(module.findTopLevelFunction("factory"), PyClass::class.java)!!
    bindReferenceAt("Local`", local)
    assertTrue(myFixture.file.text, myFixture.file.text.contains(":py:class:`Local`"))
  }

  private fun configure(@org.intellij.lang.annotations.Language("Python") text: String) {
    myFixture.configureByText("a.py", text.trimIndent())
  }

  private fun addPyFile(path: String, @org.intellij.lang.annotations.Language("Python") text: String): PyFile =
    myFixture.addFileToProject(path, text.trimIndent()) as PyFile

  /** Binds the reference at the last occurrence of [needle] to [target] in a write command. */
  private fun bindReferenceAt(needle: String, target: PsiElement) {
    val reference = myFixture.file.findReferenceAt(myFixture.file.text.lastIndexOf(needle))!!
    WriteCommandAction.runWriteCommandAction(myFixture.project) { reference.bindToElement(target) }
  }

  /** Returns the reference at a host [offset] inside an injected fragment. Fails when no fragment covers the offset. */
  private fun findInjectedReferenceAt(offset: Int): PsiReference? {
    val injectionManager = InjectedLanguageManager.getInstance(myFixture.project)
    val leaf = injectionManager.findInjectedElementAt(myFixture.file, offset)
    assertNotNull("No injected fragment at offset $offset", leaf)
    val leafHostOffset = injectionManager.injectedToHost(leaf!!, leaf.textRange.startOffset)
    return leaf.containingFile.findReferenceAt(leaf.textRange.startOffset + offset - leafHostOffset)
  }

  private fun assertResolvesTo(needle: String, expectedClass: Class<out PsiElement>, expectedName: String) {
    val offset = myFixture.file.text.lastIndexOf(needle)
    assertTrue("Marker '$needle' not found", offset >= 0)
    val reference = myFixture.file.findReferenceAt(offset)
    assertNotNull("No reference at '$needle'", reference)
    val resolved = reference!!.resolve()
    assertInstanceOf(resolved, expectedClass)
    assertEquals(expectedName, (resolved as PsiNamedElement).name)
  }
}
