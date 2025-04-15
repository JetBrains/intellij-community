// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2.GTDUOutcome
import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.jetbrains.python.codeInsight.PyTypedDictGoToDeclarationProvider
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyGotoDeclarationHandler
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.pyi.PyiUtil
import junit.framework.TestCase

class PyNavigationTest : PyTestCase() {

  // PY-35129
  fun testGoToDeclarationOnPyiFile() {
    configureByDir("onPyiFile")
    val target = PyGotoDeclarationHandler().getGotoDeclarationTarget(elementAtCaret, myFixture.editor)
    checkPyNotPyi(target)
  }

  // PY-35129
  fun testGoToImplementationOnPyiFile() {
    configureByDir("onPyiFile")
    val gotoData = CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)
    assertSize(1, gotoData.targets)
    checkPyNotPyi(gotoData.targets[0])
  }

  // PY-35129
  fun testGoToDeclarationForDirectory() {
    runWithAdditionalFileInLibDir("collections/__init__.py", "") {
      configureByDir(getTestName(true))
      val target = PyGotoDeclarationHandler().getGotoDeclarationTarget(elementAtCaret, myFixture.editor)
      checkPyNotPyi(target)
    }
  }

  fun testGoToClassField() {
    myFixture.configureByFile("${getTestName(true)}.py")
    val model = GotoSymbolModel2(myFixture.project, myFixture.testRootDisposable)
    val elements = model.getElementsByName("some_field", false, "")
    assertSize(1, elements)
    assertInstanceOf(elements.first(), PyTargetExpression::class.java)
    val expression = elements.first() as PyTargetExpression
    assertEquals("some_field", expression.name)
    assertNotNull(expression.containingClass)
    assertEquals("MyClass", expression.containingClass?.name)
  }

  // PY-42823
  fun testGotoDeclarationOrUsagesOnVariableDefinitionShowsUsages() {
    doTestGotoDeclarationOrUsagesOutcome(GTDUOutcome.SU,
                                         "v<caret>ar = 42\n" +
                                         "var = 'spam'\n" +
                                         "print(var)")
  }

  // PY-42823
  fun testGotoDeclarationOrUsagesOnVariableReassignmentNavigatesToDefinition() {
    doTestGotoDeclarationOrUsagesOutcome(GTDUOutcome.GTD,
                                         "var = 42\n" +
                                         "va<caret>r = 'spam'\n" +
                                         "print(var)")
  }

  // PY-42823
  fun testGotoDeclarationOrUsagesOnVariableUsageNavigatesToDefinition() {
    doTestGotoDeclarationOrUsagesOutcome(GTDUOutcome.GTD,
                                         "var = 42\n" +
                                         "var = 'spam'\n" +
                                         "print(va<caret>r)")
  }

  // PY-71972
  fun testGotToDeclarationOrUsagesOnInstanceAttributeDefinitionShowsUsages() {
    doTestGotoDeclarationOrUsagesOutcome(GTDUOutcome.SU, """
          class C:
              def __init__(self, val):
                  self.va<caret>l = val
          
              def __str__(self):
                  return f"{self.val} {self.val}"
          """.trimIndent()
    )
  }

  fun testGotoDeclarationOnInitialization() {
    myFixture.configureByText(
      "a.py",
      "class MyClass:\n" +
      "  pass\n" +
      "MyCla<caret>ss()"
    )
    val target = PyGotoDeclarationHandler().getGotoDeclarationTarget(elementAtCaret, myFixture.editor)
    assertInstanceOf(target, PyClass::class.java)
  }

  fun testGotoDeclarationOnInitializationWithDunderInit() {
    myFixture.configureByText(
      "a.py",
      "class MyClass:\n" +
      "  def __init__(self):\n" +
      "    pass\n" +
      "MyCla<caret>ss()"
    )
    val target = PyGotoDeclarationHandler().getGotoDeclarationTarget(elementAtCaret, myFixture.editor)
    assertInstanceOf(target, PyFunction::class.java)
  }

  fun testGotoDeclarationOnInitializationWithMetaclassDunderCall() {
    myFixture.configureByText(
      "a.py",
      "class MyMeta(type):\n" +
      "  def __call__(self, p1, p2):\n" +
      "    pass\n" +
      "class MyClass(metaclass=MyMeta):\n" +
      "  pass\n" +
      "MyCla<caret>ss()"
    )
    val target = PyGotoDeclarationHandler().getGotoDeclarationTarget(elementAtCaret, myFixture.editor)
    assertInstanceOf(target, PyClass::class.java)
  }

  fun testGotoDeclarationOnInitializationWithDunderInitAndMetaclassDunderCall() {
    myFixture.configureByText(
      "a.py",
      "class MyMeta(type):\n" +
      "  def __call__(self, p1, p2) -> object:\n" +
      "    pass\n" +
      "class MyClass(metaclass=MyMeta):\n" +
      "  def __init__(self, p3, p4):\n" +
      "    pass\n" +
      "MyCla<caret>ss()"
    )
    val target = PyGotoDeclarationHandler().getGotoDeclarationTarget(elementAtCaret, myFixture.editor)
    assertInstanceOf(target, PyClass::class.java)
  }

  fun testGotoDeclarationOnInitializationWithDunderInitOverloads() {
    // go to the first overload

    myFixture.configureByText(
      "a.py",
      "from typing import overload\n" +
      "class A:\n" +
      "    @overload\n" +
      "    def __init__(self, value: None) -> None:\n" +
      "        pass\n" +
      "    @overload\n" +
      "    def __init__(self, value: int) -> None:\n" +
      "        pass\n" +
      "    @overload\n" +
      "    def __init__(self, value: str) -> None:\n" +
      "        pass\n" +
      "<caret>A(\"abc\")"
    )

    val foo = PyGotoDeclarationHandler().getGotoDeclarationTarget(elementAtCaret, myFixture.editor) as PyFunction
    assertEquals(PyNames.INIT, foo.name)

    val context = TypeEvalContext.codeAnalysis(myFixture.project, myFixture.file)
    PyiUtil
      .getOverloads(foo, context)
      .forEach { if (it !== foo) assertTrue(PyPsiUtils.isBefore(foo, it)) }
  }

  fun testGotoDeclarationOnInitializationWithDunderInitOverloadsAndImplementation() {
    // go to the implementation

    myFixture.configureByText(
      "a.py",
      "from typing import overload\n" +
      "class A:\n" +
      "    @overload\n" +
      "    def __init__(self, value: None) -> None:\n" +
      "        pass\n" +
      "    @overload\n" +
      "    def __init__(self, value: int) -> None:\n" +
      "        pass\n" +
      "    @overload\n" +
      "    def __init__(self, value: str) -> None:\n" +
      "        pass\n" +
      "    def __init__(self, value):\n" +
      "        pass\n" +
      "<caret>A(\"abc\")"
    )

    val foo = PyGotoDeclarationHandler().getGotoDeclarationTarget(elementAtCaret, myFixture.editor) as PyFunction
    assertEquals(PyNames.INIT, foo.name)

    val context = TypeEvalContext.codeAnalysis(myFixture.project, myFixture.file)
    assertFalse(PyiUtil.isOverload(foo, context))
  }

  // PY-38636
  fun testClassInPyiAssignedToFunctionInPy() {
    myFixture.copyDirectoryToProject(getTestName(true), "")
    myFixture.configureByFile("test.py")
    val target = PyGotoDeclarationHandler().getGotoDeclarationTarget(elementAtCaret, myFixture.editor)
    TestCase.assertNotNull(target)
    assertInstanceOf(target, PyTargetExpression::class.java)
    checkPyNotPyi(target?.containingFile)
  }

  // PY-38636
  fun testStubInUserCode() {
    myFixture.copyDirectoryToProject("importFile", "")
    myFixture.configureByFile("test.py")
    runWithAdditionalClassEntryInSdkRoots(myFixture.findFileInTempDir("addRoots")) {
      val target = PyGotoDeclarationHandler().getGotoDeclarationTarget(elementAtCaret, myFixture.editor)
      checkPyNotPyi(target?.containingFile)
    }
  }

  // PY-38636
  fun testClassInPyiClassInPy() {
    myFixture.copyDirectoryToProject(getTestName(true), "")
    myFixture.configureByFile("test.py")
    val target = PyGotoDeclarationHandler().getGotoDeclarationTarget(elementAtCaret, myFixture.editor)
    TestCase.assertNotNull(target)
    assertInstanceOf(target, PyFunction::class.java)
    checkPyNotPyi(target?.containingFile)
  }

  // PY-54905
  fun testGoToImplementationFunctionInPackageWithInitPy() {
    doTestGotoImplementationNavigatesToPyNotPyi()
  }

  // PY-54905
  fun testGoToImplementationClassInPackageWithInitPy() {
    doTestGotoImplementationNavigatesToPyNotPyi()
  }

  // PY-54905 PY-54620
  fun testGoToImplementationClassInPackageWithInitPyi() {
    doTestGotoImplementationNavigatesToPyNotPyi()
  }

  // PY-54905
  fun testGoToImplementationFunctionInPyNotPyi() {
    doTestGotoImplementationNavigatesToPyNotPyi(2)
  }

  // PY-54905
  fun testGoToImplementationNameReExportedThroughAssignmentInPyiStub() {
    doTestGotoImplementationNavigatesToPyNotPyi()
  }

  // PY-61740
  fun testGoToDeclarationNameReExportedThroughAssignmentInPyiStub() {
    doTestGotoDeclarationNavigatesToPyNotPyi()
  }

  // PY-61740
  fun testGoToDeclarationNameReExportedThroughAssignmentInPyiStubTwice() {
    doTestGotoDeclarationNavigatesToPyNotPyi()
  }

  // PY-54905
  fun testGoToImplementationFunctionOverrides() {
    doTestGotoImplementationNavigatesToPyNotPyi(2)
  }

  // PY-54905
  fun testGoToImplementationClassInherits() {
    doTestGotoImplementationNavigatesToPyNotPyi(2)
  }

  // PY-61740
  fun testGoToDeclarationClassInPackageWithInitPyi() {
    doTestGotoDeclarationNavigatesToPyNotPyi()
  }

  // PY-63372
  fun testGotoDeclarationOrUsagesOnNewStyleTypeAliasDefinitionShowsUsages() {
    doTestGotoDeclarationOrUsagesOutcome(GTDUOutcome.SU,
                                         """
                                           type Al<caret>ias[T] = dict[str, T]
                                           x: Alias
                                           """)
  }

  // PY-51687
  fun testNavigationToTypedDictClass() {
    myFixture.configureByText(
      "a.py",
      "from typing import TypedDict\n" +
      "\n" +
      "\n" +
      "class Foo(TypedDict):\n" +
      "    bar: int\n" +
      "    baz: str\n" +
      "\n" +
      "\n" +
      "f = F<caret>oo(bar=1, baz=\"baz\")"
    )
    val target = PyTypedDictGoToDeclarationProvider().getGotoDeclarationTarget(elementAtCaret, myFixture.editor)
    assertInstanceOf(target, PyClass::class.java)
    assertEquals("Foo", (target as PyClass).name)
  }

  private fun doTestGotoDeclarationNavigatesToPyNotPyi() {
    myFixture.copyDirectoryToProject(getTestName(true), "")
    myFixture.configureByFile("test.py")
    val target = PyGotoDeclarationHandler().getGotoDeclarationTarget(elementAtCaret, myFixture.editor)
    checkPyNotPyi(target!!.containingFile)
  }

  private fun doTestGotoImplementationNavigatesToPyNotPyi(numTargets: Int = 1) {
    myFixture.copyDirectoryToProject(getTestName(true), "")
    myFixture.configureByFile("test.py")
    val gotoData = CodeInsightTestUtil.gotoImplementation(myFixture.editor, myFixture.file)
    assertSize(numTargets, gotoData.targets)
    for (target in gotoData.targets) {
      checkPyNotPyi(target.containingFile)
    }
  }

  private fun doTestGotoDeclarationOrUsagesOutcome(expectedOutcome: GTDUOutcome, text: String) {
    myFixture.configureByText("a.py", text)
    val actualOutcome = GotoDeclarationOrUsageHandler2.testGTDUOutcomeInNonBlockingReadAction(myFixture.editor, myFixture.file,
                                                                                              myFixture.caretOffset)
    assertEquals(expectedOutcome, actualOutcome)
  }

  private fun configureByDir(dirName: String) {
    myFixture.copyDirectoryToProject(dirName, "")
    myFixture.configureByFile("test.py")
    assertTrue(myFixture.elementAtCaret is PyiFile)
  }

  private fun checkPyNotPyi(file: PsiElement?) {
    assertTrue(file is PyFile)
    assertTrue(file !is PyiFile)
  }

  override fun getTestDataPath(): String {
    return super.getTestDataPath() + "/navigation"
  }
}