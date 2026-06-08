package com.jetbrains.python.fixtures

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.FilePropertyPusher
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType
import com.intellij.testFramework.IndexingTestUtil.Companion.waitUntilIndexesAreReady
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.text.nullize
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserSymbols.COMMENT_CHAR
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserSymbols.FIXME_KEYWORD
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserSymbols.GUIDE_BAR
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserSymbols.MARKER_CORNER
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserSymbols.MARKER_LEFT
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserSymbols.MARKER_SPAN
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserSymbols.NEWLINE
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserUtils.defaultSeverityNames
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserUtils.isAssertionMarker
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserUtils.scanTokenEnd
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserUtils.skipWhitespace
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserUtils.skipWhitespaceAndGuides
import com.jetbrains.python.codeInsight.completion.PyTestAssertionType
import com.jetbrains.python.documentation.PyTypeRenderer
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.fixtures.PyTestAssertionInliner.findCounterparts
import com.jetbrains.python.fixtures.PyTestAssertionParser.parseAssertions
import com.jetbrains.python.inspections.PyAbstractClassInspection
import com.jetbrains.python.inspections.PyArgumentListInspection
import com.jetbrains.python.inspections.PyAssertTypeInspection
import com.jetbrains.python.inspections.PyCallingNonCallableInspection
import com.jetbrains.python.inspections.PyClassVarInspection
import com.jetbrains.python.inspections.PyDataclassInspection
import com.jetbrains.python.inspections.PyDunderSlotsInspection
import com.jetbrains.python.inspections.PyEnumInspection
import com.jetbrains.python.inspections.PyFinalInspection
import com.jetbrains.python.inspections.PyInitNewSignatureInspection
import com.jetbrains.python.inspections.PyNewStyleGenericSyntaxInspection
import com.jetbrains.python.inspections.PyNewTypeInspection
import com.jetbrains.python.inspections.PyOverloadsInspection
import com.jetbrains.python.inspections.PyOverridesInspection
import com.jetbrains.python.inspections.PyProtocolInspection
import com.jetbrains.python.inspections.PyRedeclarationInspection
import com.jetbrains.python.inspections.PyTypeAliasRedeclarationInspection
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.inspections.PyTypeHintsInspection
import com.jetbrains.python.inspections.PyTypedDictInspection
import com.jetbrains.python.inspections.PyVarianceInspection
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.namespacePackages.PyNamespacePackagesService
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.IntentionalUnstubbing
import com.jetbrains.python.psi.impl.PyBuiltinCache.Companion.getInstance
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher
import com.jetbrains.python.psi.types.PyExpectedVarianceJudgment.getExpectedVariance
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.getDeclaredOrInferredVariance
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.TypeEvalContext.Companion.codeAnalysis
import com.jetbrains.python.psi.types.TypeEvalContext.Companion.userInitiated
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Unmodifiable
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestMethodOrder
import kotlin.time.Duration
import kotlin.time.measureTimedValue

/**
 * ## Assertion mini-language
 *
 * Assertions are written as Python comments in the tested source. They describe either inspection
 * highlights or custom assertions to compute at a selected PSI element.
 *
 * Assertion example that uses the marker └ to indicate at `T` in the line above and asserts that T to be of type `TypeVar`:
 *
 * ```py
 * from typing import TypeVar, Generic
 * T = TypeVar("T")
 * class Box(Generic[T]):
 * #                 └ TYPE TypeVar
 *     pass
 * ```
 *
 * Span markers indicate start and end of an AST element:
 *
 * ```py
 * def foo() -> str : ...
 * r = foo()
 * #   ^^^^^ TYPE str # With markers we can give a range to select an AST element
 * ```
 *
 * An inline assertion is written after code on the same line and applies to that whole line:
 *
 * ```py
 * i: int = "" # WARNING Expected type 'int', got 'str' instead
 * ```
 *
 * ### General Assertion Syntax
 *
 * ```
 * # <MARKER> <ASSERTION TYPE> [expected content] [FIXME [expected-after-fix]] [# free-form comment]
 * ```
 *
 * - **Markers** are either ^, └, or \. The latter one indicates an element at column 0 in the line above.
 * Markers are omitted iff the assertion is on the same line as the code.
 *
 * - **Inspection-related assertion types** are either a highlighting severity name such as `WARNING`,
 * `WEAK_WARNING`, `ERROR`, or another [HighlightSeverity] name. The content is compared with the
 * highlight description. `ISSUES *` may be used as a wildcard expectation for any inspection issue
 * on the marked line.
 *
 * - **Custom assertion types** are listed in [PyTestAssertionType]. To add a new semantic assertion kind,
 * extend [PyTestAssertionType] and handle it from the corresponding assertion computation by adding
 * a related assert method.
 *
 * - **FIXME** records the value that is expected after a known bug or limitation is fixed while keeping the
 * current/wrong expected value before it.
 *
 *
 * ## Implementation Note
 *
 * For every subclass of [PyCodeInsightTestCase], a new fixture [myFixture] is created. However, for
 * performance reasons, all test cases of the same subclass share the same [myFixture] instance, ensuring
 * consistent setup and teardown across tests within the same test class. Note that instances of [myFixture]
 * cannot be used in parallel, e.g., for parallel test execution.
 */
abstract class PyCodeInsightTestCase {
  protected val logger = thisLogger()

  protected var testCallCount = 0


  data class TestOptions(
    val enableWarnings: Boolean = true,
    val enableWeakWarnings: Boolean = true,
    val enableInfos: Boolean = false,
    val languageLevel: LanguageLevel = LanguageLevel.getLatest(),
    val assertRecursionPrevention: Boolean = true,
    val assertSdkRootsNotParsed: Boolean = true,
    val enablePyAnyType: Boolean = true,
    val enableInspections: Set<Class<out LocalInspectionTool>> = emptySet(),
    val disableInspections: Set<Class<out LocalInspectionTool>> = emptySet(),
  )


  /** Default test options in tests. Override if required. */
  open val defaultTestOptions: TestOptions = TestOptions()

  /** Default test file name in tests. Override if required. */
  open val defaultTestFileName: String = "aaa.py"

  /** Default inspections to be enabled in tests. Override if required. */
  open val defaultInspections: Set<Class<out LocalInspectionTool>> = setOf(
    PyUnresolvedReferencesInspection::class.java,
    PyTypeCheckerInspection::class.java,
    PyRedeclarationInspection::class.java,
    PyAbstractClassInspection::class.java,
    PyArgumentListInspection::class.java,
    PyAssertTypeInspection::class.java,
    PyCallingNonCallableInspection::class.java,
    PyClassVarInspection::class.java,
    PyDataclassInspection::class.java,
    PyDunderSlotsInspection::class.java,
    PyEnumInspection::class.java,
    PyFinalInspection::class.java,
    PyInitNewSignatureInspection::class.java,
    PyNewStyleGenericSyntaxInspection::class.java,
    PyNewTypeInspection::class.java,
    PyOverloadsInspection::class.java,
    PyOverridesInspection::class.java,
    PyProtocolInspection::class.java,
    PyTypedDictInspection::class.java,
    PyTypeHintsInspection::class.java,
    PyVarianceInspection::class.java,
    PyTypeAliasRedeclarationInspection::class.java,
  )

  companion object {
    @JvmStatic
    protected val ourPyLatestDescriptor = PyLightProjectDescriptor(LanguageLevel.getLatest())
    @JvmStatic
    protected lateinit var myFixture: CodeInsightTestFixture // only one active fixture per JVM is possible

    @BeforeAll
    @JvmStatic
    fun setUpFixture(testInfo: TestInfo) {
      val factory = IdeaTestFixtureFactory.getFixtureFactory()
      val testClass = testInfo.testClass.orElse(PyCodeInsightTestCase::class.java)!!
      val builder = factory.createLightFixtureBuilder(ourPyLatestDescriptor, testClass.simpleName)
      myFixture = factory.createCodeInsightFixture(builder.fixture, LightTempDirTestFixtureImpl(true))
      myFixture.testDataPath = PythonTestUtil.getTestDataPath()
      myFixture.setUp()
      InspectionProfileImpl.INIT_INSPECTIONS = true
    }

    @AfterAll
    @JvmStatic
    fun tearDownFixture() {
      InspectionProfileImpl.INIT_INSPECTIONS = false
      myFixture.tearDown()
      FilePropertyPusher.EP_NAME.findExtensionOrFail(PythonLanguageLevelPusher::class.java).flushLanguageLevelCache()
      IntentionalUnstubbing.resetForciblyUnstubbedFileSet()
    }
  }


  @BeforeEach
  fun setUpPerTest() {
    testCallCount = 0
  }

  @AfterEach
  fun tearDownPerTest() {
    runInEdtAndWait {
      // close any open editors
      val fem = FileEditorManager.getInstance(myFixture.project)
      fem.openFiles.forEach { fem.closeFile(it) }

      // wipe temp dir
      WriteAction.runAndWait<RuntimeException> {
        myFixture.tempDirFixture.getFile(".")?.children?.forEach { it.delete(this) }
      }
    }

    // reset project-scoped state
    setLanguageLevel(null)
    if (myFixture.module != null) {
      PyNamespacePackagesService.getInstance(myFixture.module).resetAllNamespacePackages()
    }

    // wait for indexes
    waitUntilIndexesAreReady(myFixture.project)
    Assertions.assertTrue(testCallCount < 2, "Test method `test` should not be called more than once per JUnit test")
  }

  protected fun setLanguageLevel(languageLevel: LanguageLevel?) {
    val project = myFixture.project
    if (project != null) {
      PythonLanguageLevelPusher.setForcedLanguageLevel(project, languageLevel)
      waitUntilIndexesAreReady(project)
    }
  }

  protected fun test(@Language("Python") fileContent: String, vararg otherFiles: Pair<String, String>) {
    test(defaultTestOptions, defaultTestFileName, fileContent, *otherFiles)
  }

  protected fun test(fileName: String, @Language("Python") fileContent: String, vararg otherFiles: Pair<String, String>) {
    test(defaultTestOptions, fileName, fileContent, *otherFiles)
  }

  protected fun test(options: TestOptions, @Language("Python") fileContent: String, vararg otherFiles: Pair<String, String>) {
    test(options, defaultTestFileName, fileContent, *otherFiles)
  }

  protected fun test(
    options: TestOptions = defaultTestOptions,
    fileName: String = defaultTestFileName,
    @Language("Python") fileContent: String,
    vararg otherFiles: Pair<String, String>,
  ) {
    if (options.assertRecursionPrevention) {
      RecursionManager.assertOnRecursionPrevention(myFixture.projectDisposable)
    }
    else {
      RecursionManager.disableAssertOnRecursionPrevention(myFixture.projectDisposable)
    }
    setLanguageLevel(options.languageLevel)

    val anyTypeKey = Registry.get("python.type.any")
    val oldAnyType = anyTypeKey.asBoolean()
    anyTypeKey.setValue(options.enablePyAnyType)

    try {
      doTest(options, fileName, fileContent, otherFiles)
    }
    finally {
      setLanguageLevel(null)
      anyTypeKey.setValue(oldAnyType)
      testCallCount++
    }
  }

  private fun doTest(options: TestOptions, fileName: String, fileContent: String, otherFiles: Array<out Pair<String, String>>) {
    for ((filename, content) in otherFiles) {
      myFixture.createFile(filename, content.trimIndent())
    }
    val currentFile = myFixture.configureByText(fileName, fileContent.trimIndent())

    val testInspections = defaultInspections - options.disableInspections + options.enableInspections
    val inspectionInstances = testInspections.map { it.getDeclaredConstructor().newInstance() }.toTypedArray()
    myFixture.enableInspections(*inspectionInstances)

    try {
      collectAndCheckHighlighting(options)
    }
    finally {
        myFixture.disableInspections(*inspectionInstances)
    }

    if (options.assertSdkRootsNotParsed) {
      runReadActionBlocking {
        assertSdkRootsNotParsed(currentFile)
      }
    }
  }

  private fun assertSdkRootsNotParsed(currentFile: PsiFile) {
    val testSdk = PythonSdkUtil.findPythonSdk(currentFile)
    if (testSdk == null) {
      logger.warn("testSdk is null. assertSdkRootsNotParsed is skipped")
      return
    }
    for (root in testSdk.rootProvider.getFiles(OrderRootType.CLASSES)) {
      assertRootNotParsed(currentFile, root)
    }
  }

  private fun assertRootNotParsed(currentFile: PsiFile, root: VirtualFile) {
    for (file in VfsUtil.collectChildrenRecursively(root)) {
      val pyFile = myFixture.psiManager.findFile(file) as? PyFile
      if (pyFile != null && pyFile != currentFile) {
        PyTestCase.assertNotParsed(pyFile)
      }
    }
  }


  private fun collectAndCheckHighlighting(options: TestOptions): Duration {
    val project = myFixture.project
    runInEdtAndWait { PsiDocumentManager.getInstance(project).commitAllDocuments() }
    val file = myFixture.file as? PsiFileImpl ?: error("Expected PsiFileImpl, got ${myFixture.file?.javaClass}")
    val document = file.fileDocument
    val expectedText = document.text

    // to load AST for changed files before it's prohibited by "fileTreeAccessFilter"
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project)

    val expectedAssertions = parseAssertions(expectedText)

    val (highlights, duration) = measureTimedValue {
      myFixture.doHighlighting()
    }
    val actualAssertions = computeAssertions(options, document, highlights, expectedAssertions)

    val actualText = PyTestAssertionInliner.generateActualText(expectedText, expectedAssertions, actualAssertions)
    if (expectedText != actualText) {
      val counterparts = findCounterparts(expectedAssertions, actualAssertions)
      val mismatchingAssertions = counterparts.entries.filter { (actual, expected) -> actual.content != expected.content }
      if (mismatchingAssertions.size == 1 && mismatchingAssertions.single().value.content.isNotBlank()) {
        val (actual, expected) = mismatchingAssertions.single()
        val idx = expectedText.indexOf(expected.content, expected.assertionOffsetStart)
        val actualTextCandidate = if (idx < 0) null else expectedText.replaceRange(idx, idx + expected.content.length, actual.content)
        if (actualText == actualTextCandidate) {
          Assertions.assertEquals(expected.toString(), actual.toString())
          return duration
        }
      }
      Assertions.assertEquals(expectedText, actualText)
    }

    return duration
  }

  private fun computeAssertions(
    options: TestOptions,
    document: Document,
    highlights: @Unmodifiable List<HighlightInfo>,
    expectedAssertions: List<PyTestAssertion>,
  ): List<PyTestAssertion> {
    val actualAssertions = mutableListOf<PyTestAssertion>()
    actualAssertions += createActualAssertionsForInspections(options, document, highlights)

    runReadActionBlocking {
      for (expectedAssertion in expectedAssertions) {
        if (defaultSeverityNames.contains(expectedAssertion.type)) {
          continue // actual inspection-related assertions have been added above
        }
        actualAssertions += createActualAssertionsForNonInspections(expectedAssertion)
      }
    }

    val actualAssertionsAligned = actualAssertions.toMutableList()
    val counterparts = findCounterparts(expectedAssertions, actualAssertionsAligned)
    for (idx in actualAssertionsAligned.lastIndex downTo 0) {
      val actualAssertion = actualAssertionsAligned[idx]
      val expectedAssertion = counterparts[actualAssertion] ?: continue
      if (expectedAssertion.type == PyTestAssertionType.ISSUES.name && actualAssertion.type != PyTestAssertionType.ISSUES.name) {
        actualAssertionsAligned.removeAt(idx)
        continue
      }

      val content = if (expectedAssertion.content == "*") "*" else actualAssertion.content
      val fixmeContent = if (expectedAssertion.fixmeContent == actualAssertion.content) "You fixed it!" else expectedAssertion.fixmeContent
      val actualAssertionFixed = actualAssertion.withContent(content, fixmeContent)
      actualAssertionsAligned[idx] = actualAssertionFixed
    }

    return actualAssertionsAligned
  }

  private fun createActualAssertionsForInspections(
    options: TestOptions,
    document: Document,
    highlights: List<HighlightInfo>,
  ): List<PyTestAssertion> {
    val actualAssertions = mutableListOf<PyTestAssertion>()

    for (highlight in highlights) {
      val typeName = when (highlight.severity) {
        HighlightSeverity.WARNING,
          -> if (options.enableWarnings) highlight.severity else continue
        HighlightSeverity.WEAK_WARNING,
          -> if (options.enableWeakWarnings) highlight.severity else continue
        @Suppress("DEPRECATION")
        HighlightSeverity.INFO,
        HighlightSeverity.INFORMATION,
          -> if (options.enableInfos) highlight.severity else continue
        HighlightInfoType.INJECTED_FRAGMENT_SEVERITY,
        HighlightInfoType.INJECTED_FRAGMENT_SYNTAX_SEVERITY,
          -> continue // ignore
        else -> highlight.severity
      }

      val codeLineStart = document.getLineNumber(highlight.startOffset)
      val codeColumnStart = highlight.startOffset - document.getLineStartOffset(codeLineStart)
      val codeLineEnd = document.getLineNumber(highlight.endOffset)
      val codeColumnEnd = highlight.endOffset - document.getLineStartOffset(codeLineEnd)
      val actualAssertion = PyTestAssertion(codeOffsetStart = highlight.startOffset,
                                            codeLineStart = codeLineStart,
                                            codeColumnStart = codeColumnStart,
                                            codeColumnEnd = codeColumnEnd,
                                            type = typeName.name,
                                            content = highlight.description ?: "")
      actualAssertions.add(actualAssertion)
    }
    return actualAssertions
  }

  private fun createActualAssertionsForNonInspections(expectedAssertion: PyTestAssertion): PyTestAssertion {
    val element = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(expectedAssertion.codeOffsetStart), PsiElement::class.java)
    if (element == null) {
      val msg = if (expectedAssertion.isInlineAssertion())
        "No element found in line ${expectedAssertion.codeLineStart}"
      else
        "No element found at position ${expectedAssertion.codeLineStart}:${expectedAssertion.codeColumnStart}"

      val actualAssertion = expectedAssertion.withContent(msg)
      return actualAssertion
    }

    var parent: PsiElement = element
    if (expectedAssertion.codeColumnEnd > 0) {
      while (parent.textRange.endOffset < expectedAssertion.codeOffsetStart - expectedAssertion.codeColumnStart + expectedAssertion.codeColumnEnd) {
        parent = parent.parent ?: break
      }
    }

    if (parent is PyStringLiteralExpression) {
      val offsetStart = expectedAssertion.codeOffsetStart - parent.textRange.startOffset - 1
      val strLitValue = parent.stringValue
      val syntheticElement = PyUtil.createExpressionFromFragment(strLitValue, parent)
                             ?: throw AssertionError("Expression not found in string literal '$strLitValue' at pos $offsetStart")
      parent = PsiTreeUtil.getParentOfType(syntheticElement.findElementAt(offsetStart), PyExpression::class.java)
                       ?: throw AssertionError("Expression not found in string literal '$strLitValue' at pos $offsetStart")
    }

    val actualContent = when (PyTestAssertionType.fromValue(expectedAssertion.type)) {
      PyTestAssertionType.TYPE -> assertType(expectedAssertion, parent)
      PyTestAssertionType.IS_BUILTIN -> assertIsBuiltin(parent)
      PyTestAssertionType.EXPECTED_VARIANCE -> assertExpectedVariance(parent)
      PyTestAssertionType.INFERRED_VARIANCE -> assertInferredVariance(parent)
      PyTestAssertionType.ISSUES -> expectedAssertion.content
      else -> "Unknown assertion type: ${expectedAssertion.type}"
    }

    val fixmeContent = if (expectedAssertion.fixmeContent == actualContent) "You fixed it!" else expectedAssertion.fixmeContent
    val actualAssertion = expectedAssertion.withContent(actualContent, fixmeContent)
    return actualAssertion
  }

  private fun assertType(expectedAssertion: PyTestAssertion, elem: PsiElement): String {
    val expr = elem as? PyTypedElement ?: elem.findParentOfType<PyTypedElement>()
    if (expr == null) {
      return "Expression not found for assertion: $expectedAssertion"
    }

    val project = expr.project
    val containingFile = expr.containingFile

    fun renderType(context: TypeEvalContext) =
      PythonDocumentationProvider.getTypeName(expr.getType(context), context, PyTypeRenderer.Feature.UNSAFE_UNION)

    val actualTypeCA = renderType(codeAnalysis(project, containingFile))
    val actualTypeUI = renderType(userInitiated(project, containingFile))

    if (actualTypeCA != actualTypeUI) {
      return "Type mismatch for code analysis context ('$actualTypeCA') and user initiated context ('$actualTypeUI')"
    }

    return actualTypeCA
  }

  private fun assertIsBuiltin(element: PsiElement): String {
    val isBuiltin = getInstance(element).isBuiltin(element)
    return if (isBuiltin) "" else "FALSE"
  }

  private fun assertExpectedVariance(element: PsiElement): String {
    val context = userInitiated(element.project, element.containingFile)
    val actualVariance = if (element is PyReferenceExpression) getExpectedVariance(element, context) else null
    return actualVariance?.name ?: "NULL"
  }

  private fun assertInferredVariance(element: PsiElement): String {
    val context = userInitiated(element.project, element.containingFile)
    val actualVariance = if (element is PyTypedElement) getDeclaredOrInferredVariance(element, context) else null
    return actualVariance?.name ?: "NULL"
  }
}

data class PyTestAssertion(
  val codeOffsetStart: Int,
  val codeLineStart: Int,
  val codeColumnStart: Int = -1,
  val codeColumnEnd: Int = -1,
  val assertionOffsetStart: Int = -1,
  val assertionOffsetEnd: Int = -1,
  val assertionLineStart: Int = -1,
  val assertionLineEnd: Int = -1,
  val assertionColumnStart: Int = -1,
  val assertionColumnEnd: Int = -1,
  /**
   * This name is usually from either [PyTestAssertionType.name] or [HighlightSeverity.name],
   * or an unknown type assertion from a wrong test assertion
   */
  val type: String,
  val content: String,
  val fixmeContent: String? = null,
  val comment: String? = null,
) {

  fun withContent(
    content: String = this.content,
    fixmeContent: String? = this.fixmeContent,
    comment: String? = this.comment,
  ): PyTestAssertion {

    return copy(content = content, fixmeContent = fixmeContent, comment = comment)
  }

  fun asInlineAssertion(): PyTestAssertion {
    return copy(codeColumnStart = -1, codeColumnEnd = -1)
  }

  fun isInlineAssertion(): Boolean {
    return codeColumnStart < 0
  }

  val codeColumnEndEffective: Int get() = if (codeColumnStart + 1 == codeColumnEnd) -1 else codeColumnEnd

  override fun toString(): String {
    val line = if (assertionLineStart > -1) assertionLineStart else codeLineStart
    val fixme = fixmeContent.nullize()?.let { "FIXME $it" } ?: ""
    val comment = comment.nullize()?.let { " # $it" } ?: ""
    return "[$line:$codeColumnStart] $type $content $fixme$comment"
  }

  fun asText(serializedAssertionsAbove: List<String> = emptyList()): Pair<String, List<String>> {
    val normalizedCodeColumnStart = if (codeColumnStart == 0) 1 else codeColumnStart

    val serializedAssertionsAboveCopy = serializedAssertionsAbove.toMutableList()
    if (normalizedCodeColumnStart > 0) {
      for (i in serializedAssertionsAbove.indices) {
        serializedAssertionsAboveCopy[i] = insertGuideAtColumn(serializedAssertionsAbove[i], normalizedCodeColumnStart)
      }
    }

    return buildAssertionText(normalizedCodeColumnStart) to serializedAssertionsAboveCopy
  }

  private fun insertGuideAtColumn(text: String, column: Int): String {
    val chars = text.toCharArray()
    if (column < 0 || column >= chars.size) {
      return text
    }
    if (chars[column] == ' ' || (
        chars[column - 1] == MARKER_SPAN
        && chars[column] == MARKER_SPAN
        && chars[column + 1] == MARKER_SPAN
                                )
    ) {
      chars[column] = GUIDE_BAR
    }
    return String(chars)
  }

  private fun buildAssertionText(normalizedCodeColumnStart: Int): String {
    val suffix = buildSuffix()

    if (normalizedCodeColumnStart < 0) {
      return "# $type$suffix"
    }

    val prefix = buildMarkerPrefix(normalizedCodeColumnStart, codeColumnEndEffective)
    return "#$prefix $type$suffix"
  }

  private fun buildSuffix(): String {
    val result = StringBuilder()

    if (content.isNotBlank()) {
      result.append(" ")
      result.append(content)
    }

    if (fixmeContent != null) {
      result.append(" ")
      result.append("FIXME")
      if (fixmeContent.isNotBlank()) {
        result.append(" ")
        result.append(fixmeContent)
      }
    }

    if (comment?.isNotBlank() == true) {
      result.append(" ")
      result.append(COMMENT_CHAR)
      result.append(" ")
      result.append(comment)
    }

    return result.toString()
  }

  private fun buildMarkerPrefix(normalizedCodeColumnStart: Int, codeColumnEnd: Int): String {
    val prefix = StringBuilder()

    repeat(normalizedCodeColumnStart - 1) {
      prefix.append(' ')
    }

    val spanLength = if (codeColumnEnd > 0) (codeColumnEnd - normalizedCodeColumnStart).coerceAtLeast(1) else 1
    when (spanLength) {
      1 if (codeColumnStart == 0) -> prefix.append(MARKER_LEFT)
      1 -> prefix.append(MARKER_CORNER)
      else -> {
        repeat(spanLength) {
          prefix.append(MARKER_SPAN)
        }
      }
    }

    return prefix.toString()
  }
}


private object PyTestAssertionInliner {

  fun generateActualText(originalText: String, expectedAssertions: List<PyTestAssertion>, actualAssertions: List<PyTestAssertion>): String {
    val counterparts = findCounterparts(expectedAssertions, actualAssertions)
    val expectedByCodeLine = expectedAssertions.groupBy { it.codeLineStart }
    val actualByCodeLine = actualAssertions.groupBy { it.codeLineStart }
    val actualText = positionActualAssertions(originalText, expectedByCodeLine, actualByCodeLine, counterparts)
    return actualText
  }

  fun findCounterparts(
    expectedAssertions: List<PyTestAssertion>,
    actualAssertions: List<PyTestAssertion>,
  ): Map<PyTestAssertion, PyTestAssertion> {

    val matches = mutableMapOf<PyTestAssertion, PyTestAssertion>()

    for (actual in actualAssertions) {
      val expectedIndex = expectedAssertions.indexOfFirst { expected ->
        areCounterparts(expected, actual)
      }

      val expected = expectedAssertions.getOrNull(expectedIndex) ?: continue
      matches[actual] = expected
    }

    return matches
  }

  private fun areCounterparts(expected: PyTestAssertion, actual: PyTestAssertion): Boolean {
    if (expected.type == PyTestAssertionType.ISSUES.name && expected.type != actual.type) {
      if (!defaultSeverityNames.contains(actual.type)) return false
    }
    else if (expected.type != actual.type) return false
    if (expected.codeColumnStart == -1) {
      return expected.codeLineStart == actual.codeLineStart
    }
    if (expected.codeOffsetStart != actual.codeOffsetStart) return false

    val expectedHasColumnEnd = expected.codeColumnEndEffective > -1
    val actualHasColumnEnd = actual.codeColumnEndEffective > -1

    if (expectedHasColumnEnd || actualHasColumnEnd) {
      return expected.codeColumnEndEffective == actual.codeColumnEndEffective
    }

    return true
  }

  private fun positionActualAssertions(
    originalText: String,
    expectedByCodeLine: Map<Int, List<PyTestAssertion>>,
    actualByCodeLine: Map<Int, List<PyTestAssertion>>,
    counterparts: Map<PyTestAssertion, PyTestAssertion>,
  ): String {
    val result = StringBuilder(originalText)

    val allCodeLines = expectedByCodeLine.keys + actualByCodeLine.keys
    val allCodeLinesSorted = allCodeLines.toSortedSet(compareByDescending { it })
    for (codeLine in allCodeLinesSorted) {
      val expectedAssertions = expectedByCodeLine[codeLine].orEmpty()
      val actualAssertions = actualByCodeLine[codeLine].orEmpty()

      val inlineAssertions = actualAssertions.filter { counterparts[it]?.isInlineAssertion() ?: false }
      if (inlineAssertions.size == 1) {
        val inlineActual = inlineAssertions.single()
        val inlineExpected = counterparts[inlineActual]!!
        val commentActuals = actualAssertions - inlineActual
        val commentExpected = expectedAssertions - inlineExpected
        replaceCommentAssertion(result, commentExpected, commentActuals)
        replaceInlineAssertion(result, inlineExpected, inlineActual)
      }
      else {
        replaceCommentAssertion(result, expectedAssertions, actualAssertions)
      }
    }

    return result.toString()
  }

  private fun replaceInlineAssertion(
    text: StringBuilder,
    expectedAssertion: PyTestAssertion,
    actualAssertion: PyTestAssertion,
  ) {

    removeAssertions(text, listOf(expectedAssertion))
    val endOfLineOffset = findLineEndOffset(text, actualAssertion)
    val (inlineText) = actualAssertion.asInlineAssertion().asText()
    text.insert(endOfLineOffset, inlineText)
  }

  private fun replaceCommentAssertion(
    text: StringBuilder,
    expectedAssertions: List<PyTestAssertion>,
    actualAssertions: List<PyTestAssertion>,
  ) {

    if (actualAssertions.isEmpty()) return
    removeAssertions(text, expectedAssertions)

    val sortedActualAssertions = actualAssertions.sortedWith(compareBy(
      { -it.codeColumnStart },
      { it.type },
      { it.content },
      { it.fixmeContent ?: "" })
    )

    var serializedAssertions = emptyList<String>()
    for (actualAssertion in sortedActualAssertions) {
      val (newAssertion, serializedAssertionsUpdated) = actualAssertion.asText(serializedAssertions)
      serializedAssertions = serializedAssertionsUpdated + newAssertion
    }

    val endOfLineOffset = findLineEndOffset(text, actualAssertions.first())
    for (serializedAssertion in serializedAssertions.asReversed()) {
      text.insert(endOfLineOffset, "\n$serializedAssertion")
    }
  }

  private fun removeAssertions(text: StringBuilder, assertions: List<PyTestAssertion>) {
    if (assertions.isEmpty()) return
    val rangeStart = assertions.minOf { it.assertionOffsetStart }
    val rangeEnd = assertions.maxOf { it.assertionOffsetEnd }

    if (rangeStart !in 0..rangeEnd || rangeEnd > text.length) {
      return
    }

    var deleteStart = rangeStart
    var deleteEnd = rangeEnd

    val allInline = assertions.all { it.isInlineAssertion() }
    if (!allInline) {
      if (deleteEnd < text.length && text[deleteEnd] == NEWLINE) {
        deleteEnd++
      }
      else if (deleteStart > 0 && text[deleteStart - 1] == NEWLINE) {
        deleteStart--
      }
    }

    text.delete(deleteStart, deleteEnd)
  }

  private fun findLineEndOffset(text: StringBuilder, assertion: PyTestAssertion): Int {
    val codeOffsetStart = assertion.codeOffsetStart
    val endOfLineOffset = text.indexOf(NEWLINE, codeOffsetStart)
    if (endOfLineOffset < 0) {
      return text.length
    }
    return endOfLineOffset
  }
}


object PyTestAssertionParser {

  fun parseAssertions(code: String): List<PyTestAssertion> {
    val lines = code.split(NEWLINE)
    val lineStartOffsets = computeLineStartOffsets(lines)
    val result = mutableListOf<PyTestAssertion>()

    var lineIndex = 0
    while (lineIndex < lines.size) {
      val parsedStart = parseAssertionStart(lines, lineIndex, lineStartOffsets)
      if (parsedStart == null) {
        lineIndex++
        continue
      }
      if (parsedStart.codeColumnStart == -1
          && PyTestAssertionType.fromValue(parsedStart.type) == null
          && !defaultSeverityNames.contains(parsedStart.type)
        ) {
        // for trailing test assertions with unknown assertion type we assume it's a comment
        lineIndex++
        continue
      }

      val payload = collectPayload(lines, lineIndex, parsedStart.initialPayload, lineStartOffsets)
      result += buildAssertion(parsedStart, payload)

      lineIndex = payload.nextLineIndex
    }

    return result
  }

  private fun computeLineStartOffsets(lines: List<String>): IntArray {
    val offsets = IntArray(lines.size)
    var currentOffset = 0

    for (i in lines.indices) {
      offsets[i] = currentOffset
      currentOffset += lines[i].length + 1
    }

    return offsets
  }

  private fun buildAssertion(parsedStart: ParsedAssertionStart, payload: CollectedPayload): PyTestAssertion {
    return PyTestAssertion(
      codeOffsetStart = parsedStart.codeOffset,
      codeLineStart = parsedStart.codeLine,
      codeColumnStart = parsedStart.codeColumnStart,
      codeColumnEnd = parsedStart.codeColumnEnd,
      assertionOffsetStart = parsedStart.assertionOffsetStart,
      assertionOffsetEnd = payload.assertionOffsetEnd,
      assertionLineStart = parsedStart.assertionLineStart,
      assertionLineEnd = payload.assertionLineEnd,
      assertionColumnStart = parsedStart.assertionColumnStart,
      assertionColumnEnd = payload.assertionColumnEnd,
      type = parsedStart.type,
      content = payload.content,
      fixmeContent = payload.fixmeContent,
      comment = payload.comment.ifEmpty { null },
    )
  }

  private fun parseAssertionStart(
    lines: List<String>,
    lineIndex: Int,
    lineStartOffsets: IntArray,
  ): ParsedAssertionStart? {
    val line = lines[lineIndex]
    val hashIndex = line.indexOf(COMMENT_CHAR)
    if (hashIndex < 0) return null

    val beforeHash = line.substring(0, hashIndex)
    val afterHash = line.substring(hashIndex + 1)

    return if (beforeHash.isBlank()) {
      parseCommentOnlyAssertion(lines, lineIndex, hashIndex, afterHash, lineStartOffsets)
    }
    else {
      parseInlineAssertion(lineIndex, hashIndex, afterHash, lineStartOffsets)
    }
  }

  private fun isAssertionStartLine(lines: List<String>, lineIndex: Int): Boolean {
    val line = lines[lineIndex]
    val hashIndex = line.indexOf(COMMENT_CHAR)
    if (hashIndex < 0) return false

    val beforeHash = line.substring(0, hashIndex)
    val afterHash = line.substring(hashIndex + 1)

    return if (beforeHash.isBlank()) {
      isCommentOnlyAssertionStart(afterHash)
    }
    else {
      isInlineAssertionStart(afterHash)
    }
  }

  private fun parseInlineAssertion(lineIndex: Int, hashIndex: Int, afterHash: String, lineStartOffsets: IntArray): ParsedAssertionStart? {
    var cursor = skipWhitespace(afterHash, 0)
    if (cursor >= afterHash.length) return null
    if (isAssertionMarker(afterHash[cursor])) return null

    val typeStart = cursor
    cursor = scanTokenEnd(afterHash, cursor)
    if (cursor <= typeStart) return null

    val type = afterHash.substring(typeStart, cursor)
    val payload = afterHash.substring(cursor).trimStart()

    val assertionColumnEnd = lineIndexColumnEnd(hashIndex, afterHash)
    val assertionOffsetStart = lineStartOffsets[lineIndex] + hashIndex

    val codeColumnStart = -1
    val codeColumnEnd = -1
    val codeOffset = lineStartOffsets[lineIndex]

    return ParsedAssertionStart(
      codeOffset = codeOffset,
      codeLine = lineIndex,
      codeColumnStart = codeColumnStart,
      codeColumnEnd = codeColumnEnd,
      assertionOffsetStart = assertionOffsetStart,
      assertionLineStart = lineIndex,
      assertionColumnStart = hashIndex,
      assertionColumnEnd = assertionColumnEnd,
      type = type,
      initialPayload = payload,
    )
  }

  private fun parseCommentOnlyAssertion(
    lines: List<String>,
    lineIndex: Int,
    hashIndex: Int,
    afterHash: String,
    lineStartOffsets: IntArray,
  ): ParsedAssertionStart? {
    val cursor = skipWhitespaceAndGuides(afterHash)
    if (cursor >= afterHash.length) return null

    val marker = afterHash[cursor]
    if (!isAssertionMarker(marker)) return null

    val codeColumnStart = hashIndex + 1 + cursor - (if (marker == MARKER_LEFT) 1 else 0)

    var scanIndex = cursor + 1
    var codeColumnEnd = -1
    while (scanIndex < afterHash.length && isAssertionMarker(afterHash[scanIndex])) {
      codeColumnEnd = hashIndex + 1 + scanIndex
      scanIndex++
    }

    scanIndex = skipWhitespace(afterHash, scanIndex)
    if (scanIndex >= afterHash.length) return null

    val typeStart = scanIndex
    scanIndex = scanTokenEnd(afterHash, scanIndex)
    if (scanIndex <= typeStart) return null

    val type = afterHash.substring(typeStart, scanIndex)
    val payload = afterHash.substring(scanIndex).trimStart()
    val codeLine = findReferencedCodeLine(lines, lineIndex) ?: return null
    val codeOffset = lineStartOffsets[codeLine] + codeColumnStart

    val assertionColumnEnd = lineIndexColumnEnd(hashIndex, afterHash)
    val assertionOffsetStart = lineStartOffsets[lineIndex] + hashIndex

    return ParsedAssertionStart(
      codeOffset = codeOffset,
      codeLine = codeLine,
      codeColumnStart = codeColumnStart,
      codeColumnEnd = codeColumnEnd,
      assertionOffsetStart = assertionOffsetStart,
      assertionLineStart = lineIndex,
      assertionColumnStart = hashIndex,
      assertionColumnEnd = assertionColumnEnd,
      type = type,
      initialPayload = payload,
    )
  }

  private fun collectPayload(
    lines: List<String>,
    assertionStartLineIndex: Int,
    initialPayload: String,
    lineStartOffsets: IntArray,
  ): CollectedPayload {
    val contentBuilder = StringBuilder()
    val fixmeBuilder = StringBuilder()
    val commentBuilder = StringBuilder()
    var inFixme = false

    val firstLineSplit = splitTrailingComment(initialPayload)
    val firstSplit = splitAtFixme(firstLineSplit.beforeComment)
    appendIfNotEmpty(contentBuilder, firstSplit.beforeFixme)
    if (firstSplit.afterFixme != null) {
      inFixme = true
      appendIfNotEmpty(fixmeBuilder, firstSplit.afterFixme)
    }
    appendIfNotEmpty(commentBuilder, firstLineSplit.comment)

    var lineIndex = assertionStartLineIndex + 1
    var assertionLineEnd = assertionStartLineIndex
    var assertionColumnEnd = lineIndexColumnEnd(
      lines[assertionStartLineIndex].indexOf(COMMENT_CHAR),
      lines[assertionStartLineIndex].substring(lines[assertionStartLineIndex].indexOf(COMMENT_CHAR) + 1)
    )
    var assertionOffsetEnd = computeLineEndOffset(
      lineStartOffsets,
      assertionStartLineIndex,
      lines[assertionStartLineIndex]
    )

    while (lineIndex < lines.size) {
      val line = lines[lineIndex]
      if (!isCommentLine(line)) break
      if (isAssertionStartLine(lines, lineIndex)) break

      val continuationPayload = extractCommentPayload(line)
      val continuationSplit = splitTrailingComment(continuationPayload)

      if (!inFixme) {
        val split = splitAtFixme(continuationSplit.beforeComment)
        appendIfNotEmpty(contentBuilder, split.beforeFixme)
        if (split.afterFixme != null) {
          inFixme = true
          appendIfNotEmpty(fixmeBuilder, split.afterFixme)
        }
      }
      else {
        appendIfNotEmpty(fixmeBuilder, continuationSplit.beforeComment.trimEnd())
      }

      appendIfNotEmpty(commentBuilder, continuationSplit.comment)

      assertionLineEnd = lineIndex
      assertionColumnEnd = lines[lineIndex].length
      assertionOffsetEnd = computeLineEndOffset(lineStartOffsets, lineIndex, lines[lineIndex])
      lineIndex++
    }

    return CollectedPayload(
      content = contentBuilder.toString(),
      fixmeContent = if (inFixme) fixmeBuilder.toString() else null,
      comment = commentBuilder.toString(),
      nextLineIndex = lineIndex,
      assertionLineEnd = assertionLineEnd,
      assertionColumnEnd = assertionColumnEnd,
      assertionOffsetEnd = assertionOffsetEnd,
    )
  }

  private fun splitAtFixme(text: String): FixmeSplit {
    val idx = text.indexOf(FIXME_KEYWORD)
    return if (idx >= 0) {
      FixmeSplit(
        beforeFixme = text.substring(0, idx).trimEnd(),
        afterFixme = text.substring(idx + FIXME_KEYWORD.length).trimStart(),
      )
    }
    else {
      FixmeSplit(
        beforeFixme = text.trimEnd(),
        afterFixme = null,
      )
    }
  }

  private fun splitTrailingComment(text: String): CommentSplit {
    val idx = text.indexOf(COMMENT_CHAR)
    return if (idx >= 0) {
      CommentSplit(
        beforeComment = text.substring(0, idx).trimEnd(),
        comment = text.substring(idx + 1).trim(),
      )
    }
    else {
      CommentSplit(
        beforeComment = text.trimEnd(),
        comment = null,
      )
    }
  }

  private fun appendIfNotEmpty(builder: StringBuilder, text: String?) {
    if (text.isNullOrEmpty()) return
    if (builder.isNotEmpty()) builder.append(NEWLINE)
    builder.append(text)
  }

  private fun extractCommentPayload(line: String): String {
    val hashIndex = line.indexOf(COMMENT_CHAR)
    if (hashIndex < 0) return ""
    return line.substring(hashIndex + 1).trimStart()
  }

  private fun isCommentLine(line: String): Boolean {
    return line.trimStart().startsWith(COMMENT_CHAR)
  }

  private fun isInlineAssertionStart(afterHash: String): Boolean {
    val cursor = skipWhitespace(afterHash, 0)
    if (cursor >= afterHash.length) return false
    return !isAssertionMarker(afterHash[cursor])
  }

  private fun isCommentOnlyAssertionStart(afterHash: String): Boolean {
    val cursor = skipWhitespaceAndGuides(afterHash)
    if (cursor >= afterHash.length) return false
    return isAssertionMarker(afterHash[cursor])
  }

  private fun findReferencedCodeLine(lines: List<String>, assertionLineIndex: Int): Int? {
    var i = assertionLineIndex - 1
    while (i >= 0) {
      if (!isCommentLine(lines[i])) {
        return i
      }
      i--
    }
    return null
  }

  private fun lineIndexColumnEnd(hashIndex: Int, afterHash: String): Int {
    return hashIndex + afterHash.length
  }

  private fun computeLineEndOffset(lineStartOffsets: IntArray, lineIndex: Int, line: String): Int {
    return lineStartOffsets[lineIndex] + line.length
  }


  private data class ParsedAssertionStart(
    val codeOffset: Int,
    val codeLine: Int,
    val codeColumnStart: Int,
    val codeColumnEnd: Int,
    val assertionOffsetStart: Int,
    val assertionLineStart: Int,
    val assertionColumnStart: Int,
    val assertionColumnEnd: Int,
    val type: String,
    val initialPayload: String,
  )

  private data class FixmeSplit(
    val beforeFixme: String,
    val afterFixme: String?,
  )

  private data class CommentSplit(
    val beforeComment: String,
    val comment: String?,
  )

  private data class CollectedPayload(
    val content: String,
    val fixmeContent: String?,
    val comment: String,
    val nextLineIndex: Int,
    val assertionLineEnd: Int,
    val assertionColumnEnd: Int,
    val assertionOffsetEnd: Int,
  )
}


class PyCodeInsightTestCaseAssertionParserAndInlinerTest {

  @Test
  fun `parser parses marker span with multiline payload fixme and trailing comment`() {
    val code = """
      value = make()
      #   ^^^ TYPE before line 1
      # before line 2 FIXME after line 1
      # after line 2 # trailing note
    """.trimIndent()

    val assertion = parseAssertions(code).single()
    Assertions.assertEquals(0, assertion.codeLineStart)
    Assertions.assertEquals(4, assertion.codeColumnStart)
    Assertions.assertEquals(6, assertion.codeColumnEnd)
    Assertions.assertEquals(1, assertion.assertionLineStart)
    Assertions.assertEquals(3, assertion.assertionLineEnd)
    Assertions.assertEquals("TYPE", assertion.type)
    Assertions.assertEquals("before line 1\nbefore line 2", assertion.content)
    Assertions.assertEquals("after line 1\nafter line 2", assertion.fixmeContent)
    Assertions.assertEquals("trailing note", assertion.comment)
  }

  @Test
  fun `parser ignores inline comments with unknown assertion type`() {
    val code = """
      x = 1 # TODO plain comment
      y = 2 # TYPE int
    """.trimIndent()

    val assertions = parseAssertions(code)
    Assertions.assertEquals(1, assertions.size)
    val assertion = assertions.single()
    Assertions.assertEquals(1, assertion.codeLineStart)
    Assertions.assertEquals(-1, assertion.codeColumnStart)
    Assertions.assertEquals("TYPE", assertion.type)
    Assertions.assertEquals("int", assertion.content)
  }

  @Test
  fun `parser resolves left marker to column zero`() {
    val code = """
      n = 1
      #\ TYPE int
    """.trimIndent()

    val assertion = parseAssertions(code).single()
    Assertions.assertEquals(0, assertion.codeColumnStart)
    Assertions.assertEquals(-1, assertion.codeColumnEnd)
    Assertions.assertEquals("TYPE", assertion.type)
    Assertions.assertEquals("int", assertion.content)
  }

  @Test
  fun `parser stops payload collection at next assertion start`() {
    val code = """
      value = 1
      #   └ TYPE first line
      # continuation
      #   └ TYPE second line
    """.trimIndent()

    val assertions = parseAssertions(code)
    Assertions.assertEquals(2, assertions.size)
    Assertions.assertEquals("first line\ncontinuation", assertions[0].content)
    Assertions.assertEquals("second line", assertions[1].content)
  }

  @Test
  fun `inliner replaces comment assertion content`() {
    val original = """
      value = call()
      #       └ TYPE old
    """.trimIndent()

    val expectedAssertions = parseAssertions(original)
    val actualAssertions = listOf(expectedAssertions.single().withContent("new"))
    val actualText = PyTestAssertionInliner.generateActualText(original, expectedAssertions, actualAssertions)

    Assertions.assertEquals(
      """
      value = call()
      #       └ TYPE new
      """.trimIndent(),
      actualText
    )
  }

  @Test
  fun `inliner replaces inline and comment assertions on the same code line`() {
    val original = """
      x = foo() # WARNING old-inline
      #    └ TYPE old-comment
    """.trimIndent()

    val expectedAssertions = parseAssertions(original)
    val inlineExpected = expectedAssertions.single { it.isInlineAssertion() }
    val commentExpected = expectedAssertions.single { !it.isInlineAssertion() }
    val actualAssertions = listOf(
      inlineExpected.withContent("new-inline"),
      commentExpected.withContent("new-comment"),
    )

    val actualText = PyTestAssertionInliner.generateActualText(original, expectedAssertions, actualAssertions)

    Assertions.assertEquals(
      """
      x = foo() # WARNING new-inline
      #    └ TYPE new-comment
      """.trimIndent(),
      actualText
    )
  }

  @Test
  fun `inliner matches ISSUES expectation with severity assertions`() {
    val original = """
      x = 1 # ISSUES *
    """.trimIndent()

    val expectedAssertions = parseAssertions(original)
    val actualAssertions = listOf(expectedAssertions.single().copy(type = HighlightSeverity.WARNING.name, content = "found warning"))
    val actualText = PyTestAssertionInliner.generateActualText(original, expectedAssertions, actualAssertions)

    Assertions.assertEquals(
      """
      x = 1 # WARNING found warning
      """.trimIndent(),
      actualText
    )
  }
}


/**
 * Verifies that within a single subclass of [PyCodeInsightTestCase], two consecutive test
 * methods observe:
 *  1. The very same [myFixture] instance (it is re-used across the methods of the subclass),
 *  2. A re-initialized per-test state (open editors closed, temp dir wiped, [testCallCount] reset).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class PyCodeInsightTestCaseFixtureReuseTest : PyCodeInsightTestCase() {

  companion object {
    private var fixtureFromFirstTest: CodeInsightTestFixture? = null
    private var projectFromFirstTest: com.intellij.openapi.project.Project? = null
    private var tempDirFromFirstTest: com.intellij.testFramework.fixtures.TempDirTestFixture? = null
  }

  @Test
  @Order(1)
  fun `first test populates the fixture and leaves state behind`() {
    // Configure a file and open it in the editor; this is project-scoped state that the
    // per-test tear-down is expected to clean up before the next test starts.
    myFixture.configureByText("reuse_first.py", "x = 1\n")
    val fem = FileEditorManager.getInstance(myFixture.project)
    Assertions.assertTrue(fem.openFiles.isNotEmpty(), "An editor should be open after configureByText")

    // Create an extra file in the temp dir to verify the temp dir gets wiped between tests.
    myFixture.createFile("reuse_extra.py", "y = 2\n")
    val tempRoot = myFixture.tempDirFixture.getFile(".")
    Assertions.assertNotNull(tempRoot, "Temp dir should exist")
    Assertions.assertTrue(tempRoot!!.children.isNotEmpty(), "Some files should exist in the temp dir at this point")

    // Remember identities so the second test can compare against them.
    fixtureFromFirstTest = myFixture
    projectFromFirstTest = myFixture.project
    tempDirFromFirstTest = myFixture.tempDirFixture
  }

  @Test
  @Order(2)
  fun `second test reuses the same fixture but observes re-initialized state`() {
    Assertions.assertNotNull(fixtureFromFirstTest, "First test must have run before the second one")

    // (1) Fixture re-use: the same instance is observed across test methods of this subclass.
    Assertions.assertSame(fixtureFromFirstTest, myFixture,
                          "myFixture must be the same instance across tests of the same PyCodeInsightTestCase subclass")
    Assertions.assertSame(projectFromFirstTest, myFixture.project,
                          "The project owned by myFixture must be the same instance across tests")
    Assertions.assertSame(tempDirFromFirstTest, myFixture.tempDirFixture,
                          "The temp dir fixture must be the same instance across tests")

    // (2) Re-initialization: open editors from the previous test were closed by tearDownPerTest.
    val fem = FileEditorManager.getInstance(myFixture.project)
    Assertions.assertTrue(fem.openFiles.isEmpty(),
                          "All editors opened in the previous test should be closed before the next test starts")

    // (3) Re-initialization: the temp dir was wiped by tearDownPerTest.
    val tempRoot = myFixture.tempDirFixture.getFile(".")
    Assertions.assertNotNull(tempRoot, "Temp dir should still exist (it belongs to the shared fixture)")
    Assertions.assertEquals(0, tempRoot!!.children.size,
                            "Temp dir contents from the previous test should be wiped before the next test starts")

    // (4) Re-initialization: the per-test counter is reset by setUpPerTest.
    Assertions.assertEquals(0, testCallCount,
                            "testCallCount must be reset to 0 at the start of every test")

    // The shared fixture remains fully functional after re-initialization.
    myFixture.configureByText("reuse_second.py", "z = 3\n")
    Assertions.assertNotNull(myFixture.file, "Fixture should still be usable in a subsequent test")
  }
}
