package com.jetbrains.python.fixtures

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.idea.TestFor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.classRoots
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.IndexingTestUtil.Companion.waitUntilIndexesAreReady
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.containers.orNull
import com.intellij.python.testAssertions.PyTestAssertion
import com.intellij.python.testAssertions.PyTestAssertionFix
import com.intellij.python.testAssertions.PyTestAssertionInliner
import com.intellij.python.testAssertions.PyTestAssertionInliner.findCounterparts
import com.intellij.python.testAssertions.PyTestAssertionParseResult
import com.intellij.python.testAssertions.PyTestAssertionParser
import com.intellij.python.testAssertions.PyTestAssertionSyntax
import com.intellij.python.testAssertions.PyTestAssertionSyntax.NEWLINE
import com.intellij.python.testAssertions.PyTestAssertionText
import com.intellij.python.testAssertions.PyTestDirectives
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.documentation.PyDocumentationSettings
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.documentation.docstrings.DocStringFormat
import com.jetbrains.python.inspections.PyAbstractClassInspection
import com.jetbrains.python.inspections.PyArgumentListInspection
import com.jetbrains.python.inspections.PyAssertTypeInspection
import com.jetbrains.python.inspections.PyAttrsDataclassInspection
import com.jetbrains.python.inspections.PyCallingNonCallableInspection
import com.jetbrains.python.inspections.PyClassVarInspection
import com.jetbrains.python.inspections.PyDataclassInspection
import com.jetbrains.python.inspections.PyDataclassTransformInspection
import com.jetbrains.python.inspections.PyDunderSlotsInspection
import com.jetbrains.python.inspections.PyEnumInspection
import com.jetbrains.python.inspections.PyFinalInspection
import com.jetbrains.python.inspections.PyInitNewSignatureInspection
import com.jetbrains.python.inspections.PyMethodOverridingInspection
import com.jetbrains.python.inspections.PyNewStyleGenericSyntaxInspection
import com.jetbrains.python.inspections.PyNewTypeInspection
import com.jetbrains.python.inspections.PyOverloadsInspection
import com.jetbrains.python.inspections.PyOverridesInspection
import com.jetbrains.python.inspections.PyProtocolInspection
import com.jetbrains.python.inspections.PyStdlibDataclassInspection
import com.jetbrains.python.inspections.PyTypeAliasRedeclarationInspection
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.inspections.PyTypeHintsInspection
import com.jetbrains.python.inspections.PyTypedDictInspection
import com.jetbrains.python.inspections.PyVarianceInspection
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.namespacePackages.PyNamespacePackagesService
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.IntentionalUnstubbing
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyExpectedTypeJudgement.getExpectedType
import com.jetbrains.python.psi.types.PyExpectedVarianceJudgment.getExpectedVariance
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.getDeclaredOrInferredVariance
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.TypeEvalContext.Companion.codeAnalysis
import com.jetbrains.python.psi.types.TypeEvalContext.Companion.userInitiated
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
import org.junit.jupiter.api.fail
import java.lang.reflect.Method
import kotlin.reflect.KClass
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
 * - **Markers** are either ^, └, or \. The latter one indicates the element at the column of the `#` in the line above.
 * Markers are omitted iff the assertion is on the same line as the code. A marker line can be indented, for example
 * inside a function body. The `#` keeps its column. A marker points at the nearest code line or plain comment above it.
 * The payload lines of an assertion are not such lines. A span cannot start with `\`, so a span from the column of the `#`
 * starts in the next column.
 *
 * - **Payload**: a `#` starts the trailing free-form comment. Write `\#` for a `#` in the expected content.
 * A payload can continue on the next comment lines when their `#` is in the same column.
 * Each payload line can have its own trailing comment. A comment line with a marker and a word that starts with a letter
 * starts a new assertion. Any other comment line in the same column continues the payload.
 *
 * - **Inspection-related assertion types** are either a highlighting severity name such as `WARNING`,
 * `WEAK_WARNING`, `ERROR`, or another [HighlightSeverity] name with `_` in place of a space. The content is compared with the
 * highlight description. `ISSUES *` may be used as a wildcard expectation for any inspection issue
 * on the marked line.
 *
 * - **Custom assertion types** are listed in [PyTestDirectives]. To add a new semantic assertion kind,
 * add an entry to [PyTestDirectives] and handle it in the assertion computation of this class.
 * Directive names are case-sensitive.
 *
 * - **FIXME** records the value that is expected after a known bug or limitation is fixed while keeping the
 * current/wrong expected value before it. `FIXME` may also appear with no anticipated text to indicate e.g.,
 * superfluous warnings that should disappear once a bug is fixed. `FIXME` counts only as a whole word.
 *
 * ### Canonical Form
 *
 * A failed test shows the actual text in the form that [PyTestAssertionInliner] writes. So the expected text must have
 * that form too, and the test fails on a text in another form. The rules of the canonical form:
 * - A marker of one column is `└`, or `\` in the column of the `#`. A span of two or more columns is `^^`.
 * - One space follows the `#` of an inline assertion, the marker and the directive name.
 * - The marker lines of one code line start with the rightmost marker. A `│` guide continues each marker of a lower line
 *   through the lines above it. The ASCII guide `|` is not canonical.
 * - A further payload line starts with `# `, and an empty payload line is a `#` alone.
 *
 *
 * ## Implementation Note
 *
 * For every subclass of [PyCodeInsightTestCase], a new fixture [myFixture] is created. However, for
 * performance reasons, all test cases of the same subclass share the same [myFixture] instance, ensuring
 * consistent setup and teardown across tests within the same test class. Note that instances of [myFixture]
 * cannot be used in parallel, e.g., for parallel test execution. A test whose [fixtureKey] differs from the key of
 * the shared fixture gets a new fixture.
 */
abstract class PyCodeInsightTestCase {
  protected val logger = thisLogger()

  protected var testCallCount = 0

  /** A Python triple quote, for a docstring inside a Kotlin raw string: `"""$tripleQuote doc $tripleQuote"""`. */
  protected val tripleQuote: String = "\"\"\""


  @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
  @Retention(AnnotationRetention.RUNTIME)
  annotation class TestInspections(
    val enableInspections: Array<KClass<out LocalInspectionTool>> = [],
    val disableInspections: Array<KClass<out LocalInspectionTool>> = [],
  )

  private fun TestInspections.enableInspectionsAsClasses(): Set<Class<out LocalInspectionTool>> =
    this.enableInspections.mapTo(mutableSetOf()) { it.java }

  private fun TestInspections.disableInspectionsAsClasses(): Set<Class<out LocalInspectionTool>> =
    this.disableInspections.mapTo(mutableSetOf()) { it.java }


  @Target(AnnotationTarget.FUNCTION) // on purpose not including AnnotationTarget.CLASS to avoid users overseeing options
  @Retention(AnnotationRetention.RUNTIME)
  annotation class TestCaseOptions(
    val testFileName: String = "aaa.py",
    val enableWarnings: Boolean = true,
    val enableWeakWarnings: Boolean = true,
    val enableInfos: Boolean = false,
    val languageLevel: LanguageLevel = LanguageLevel.PYTHON315,
    val assertRecursionPrevention: Boolean = true,
    val assertSdkRootsNotParsed: Boolean = true,
    val enablePyAnyType: Boolean = true,
    val copyDirectoryToProject: Array<CopyDirectory> = [],
    val additionalSdkRoots: Array<SdkRoot> = [],
    /** The boolean registry keys that the test sets to true. */
    val enableRegistryKeys: Array<String> = [],
    /** The boolean registry keys that the test sets to false. */
    val disableRegistryKeys: Array<String> = [],
  )

  annotation class CopyDirectory(
    val source: String,
    val destination: String,
  )

  annotation class SdkRoot(
    val path: String,
    val type: OrderRootTypeEnum,
  )

  enum class OrderRootTypeEnum(val orderRootType: OrderRootType) {
    CLASSES(OrderRootType.CLASSES),
    SOURCES(OrderRootType.SOURCES),

    @Suppress("unused")
    DOCUMENTATION(OrderRootType.DOCUMENTATION);
  }

  /** Default inspections to be enabled in tests. Override if required. */
  open val defaultInspections: Set<Class<out LocalInspectionTool>> = setOf(
    PyUnresolvedReferencesInspection::class.java,
    PyTypeCheckerInspection::class.java,
    PyAbstractClassInspection::class.java,
    PyArgumentListInspection::class.java,
    PyAssertTypeInspection::class.java,
    PyCallingNonCallableInspection::class.java,
    PyClassVarInspection::class.java,
    PyDataclassInspection::class.java,
    PyStdlibDataclassInspection::class.java,
    PyAttrsDataclassInspection::class.java,
    PyDataclassTransformInspection::class.java,
    PyDunderSlotsInspection::class.java,
    PyEnumInspection::class.java,
    PyFinalInspection::class.java,
    PyInitNewSignatureInspection::class.java,
    PyMethodOverridingInspection::class.java,
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

  private lateinit var myTestInspections: TestInspections
  private lateinit var myTestCaseOptions: TestCaseOptions

  /**
   * Creates and sets up the fixture for [testCaseOptions]. The default is a light project with a mock SDK of
   * [TestCaseOptions.languageLevel].
   */
  protected open fun createFixture(testCaseOptions: TestCaseOptions): CodeInsightTestFixture {
    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val projectDescriptor = PyLightProjectDescriptor(testCaseOptions.languageLevel)
    val builder = factory.createLightFixtureBuilder(projectDescriptor, testClassName)
    val fixture = factory.createCodeInsightFixture(builder.fixture, LightTempDirTestFixtureImpl(true))
    fixture.testDataPath = PythonTestUtil.getTestDataPath()
    fixture.setUp()
    return fixture
  }

  /** Tears down a [fixture] of [createFixture]. */
  protected open fun disposeFixture(fixture: CodeInsightTestFixture) {
    fixture.tearDown()
  }

  /**
   * The values that the fixture of [createFixture] depends on. A test gets a new fixture when its key is not equal to
   * the key of the shared fixture. A subclass with its own fixture option adds the option to the key of the superclass.
   */
  protected open fun fixtureKey(testCaseOptions: TestCaseOptions): List<Any?> =
    listOf(testCaseOptions.languageLevel, testCaseOptions.enablePyAnyType, testCaseOptions.assertSdkRootsNotParsed)

  companion object {
    private data class CachedFixture(val key: List<Any?>, val fixture: CodeInsightTestFixture, val dispose: () -> Unit)

    private lateinit var testClassName: String
    private var cachedFixture: CachedFixture? = null

    @JvmStatic
    protected val myFixture: CodeInsightTestFixture
      get() = cachedFixture?.fixture ?: error("No fixture set up")

    /** Looks a function up in [file] by its name, or by `<class>.<method>` for a method. */
    @JvmStatic
    protected fun findFunction(file: PyFile, name: String): PyFunction {
      val parts = name.split('.')
      return if (parts.size == 1) file.findTopLevelFunction(name)!!
      else file.findTopLevelClass(parts[0])!!.findMethodByName(parts[1], false, null)!!
    }

    @BeforeAll
    @JvmStatic
    fun setUpTestClass(testInfo: TestInfo) {
      val testClass = testInfo.testClass.orNull() ?: PyCodeInsightTestCase::class.java
      testClassName = testClass.simpleName
    }

    private fun ensureFixture(testCase: PyCodeInsightTestCase, testCaseOptions: TestCaseOptions) {
      val key = testCase.fixtureKey(testCaseOptions)
      if (cachedFixture?.key == key) return

      disposeCachedFixture()
      val fixture = testCase.createFixture(testCaseOptions)
      cachedFixture = CachedFixture(key, fixture) { testCase.disposeFixture(fixture) }
      // The setup registers a new SDK, and the Python project structure follows it in the background. A test that
      // highlights before that lands sees the old structure, and the restart that follows cancels its highlighting.
      awaitPythonInterpreters(fixture.project)
      InspectionProfileImpl.INIT_INSPECTIONS = true
      Registry.get(PyAnyType.REGISTRY_KEY).setValue(testCaseOptions.enablePyAnyType)
    }

    /** Sets the keys of [enable] to true and the keys of [disable] to false until [parentDisposable] is disposed. */
    internal fun setRegistryKeys(enable: Array<String>, disable: Array<String>, parentDisposable: Disposable) {
      for (key in enable) {
        Registry.get(key).setValue(true, parentDisposable)
      }
      for (key in disable) {
        Registry.get(key).setValue(false, parentDisposable)
      }
    }

    @AfterAll
    @JvmStatic
    fun tearDownFixture() {
      RunAll.runAll(
        { IntentionalUnstubbing.resetForciblyUnstubbedFileSet() },
        { Registry.get(PyAnyType.REGISTRY_KEY).resetToDefault() },
        { InspectionProfileImpl.INIT_INSPECTIONS = false },
        { disposeCachedFixture() },
      )
    }

    /**
     * Disposes the cached fixture and closes its light project. The platform keeps a light project open for the next
     * fixture of an equal descriptor, but it disposes the module of that project. A new fixture gets a new project, so
     * the project and its module start and end together.
     */
    private fun disposeCachedFixture() {
      val cached = cachedFixture ?: return
      cachedFixture = null
      RunAll.runAll(
        { cached.dispose() },
        // The teardown of a class does not run on the EDT, and a project closes there.
        { ApplicationManager.getApplication().invokeAndWait { WriteIntentReadAction.run { LightPlatformTestCase.closeAndDeleteProject() } } },
      )
    }
  }


  @BeforeEach
  fun setUpPerTest(testInfo: TestInfo) {
    testCallCount = 0
    myTestMethod = testInfo.testMethod.orNull()
    myTestClass = testInfo.testClass.orNull()
    myTestInspections = findTestAnnotation(TestInspections::class.java) ?: TestInspections()
    myTestCaseOptions = myTestMethod?.getAnnotation(TestCaseOptions::class.java) ?: TestCaseOptions()
    ensureFixture(this, myTestCaseOptions)
  }

  private var myTestMethod: Method? = null
  private var myTestClass: Class<*>? = null

  /**
   * Finds [annotationClass] on the current test method, then on the running test class and its superclasses, then on
   * each enclosing class and its superclasses. The value is available from [fixtureKey] and [createFixture] on.
   */
  protected fun <A : Annotation> findTestAnnotation(annotationClass: Class<A>): A? {
    myTestMethod?.getAnnotation(annotationClass)?.let { return it }
    val testClass = myTestClass ?: myTestMethod?.declaringClass ?: return null
    return generateSequence(testClass) { it.enclosingClass }
      .flatMap { enclosing -> generateSequence(enclosing) { it.superclass } }
      .firstNotNullOfOrNull { it.getAnnotation(annotationClass) }
  }

  @AfterEach
  fun tearDownPerTest() {
    RunAll.runAll(
      {
        runInEdtAndWait {
          // close any open editors
          val fem = FileEditorManager.getInstance(myFixture.project)
          fem.openFiles.forEach { fem.closeFile(it) }
          EditorHistoryManager.getInstance(myFixture.project).removeAllFiles()
          // wipe temp dir
          WriteAction.runAndWait<RuntimeException> {
            myFixture.tempDirFixture.getFile(".")?.children?.forEach { it.delete(this) }
          }
        }
      },
      { if (myFixture.module != null) PyNamespacePackagesService.getInstance(myFixture.module).resetAllNamespacePackages() },
      { waitUntilIndexesAreReady(myFixture.project) },
      { Assertions.assertTrue(testCallCount < 2, "One of `test`, `testQuickFix` and `assertNoQuickFix` should be called only once per JUnit test") },
    )
  }

  protected fun setAdditionalSdkRoots(additionalSdkRoots: Array<SdkRoot>, addElseRemove: Boolean) {
    if (additionalSdkRoots.isEmpty()) return
    val interpreter = findPythonInterpreterForTests(myFixture.module)!!
    val roots = additionalSdkRoots.map { sdkRoot ->
      val absPath = PythonTestUtil.getTestDataPath() + "/" + sdkRoot.path
      val testDataDir = StandardFileSystems.local().findFileByPath(absPath) ?: fail("Could not find additional SDK root at $absPath")
      sdkRoot to testDataDir
    }
    // A class root is a path that the user added to the interpreter, as in the interpreter settings: the roots of the
    // interpreter follow its facts, so a class root that the test wrote into the SDK goes with the next refresh.
    val details = PythonInterpreterDetails.getInstance(myFixture.project)
    val addedPaths = details.facts(interpreter).addedPaths.toMutableList()
    for ([_, root] in roots.filter { it.first.type == OrderRootTypeEnum.CLASSES }) {
      if (addElseRemove) addedPaths.add(root.toNioPath()) else addedPaths.remove(root.toNioPath())
    }
    details.writeAddedPaths(interpreter, addedPaths)

    val otherRoots = roots.filter { it.first.type != OrderRootTypeEnum.CLASSES }
    if (otherRoots.isNotEmpty()) {
      @Suppress("DEPRECATION")
      val sdk = interpreter.getSdkAPI()
      runWriteAction {
        val modificator = sdk.getSdkModificator()
        for ([sdkRoot, root] in otherRoots) {
          if (addElseRemove) modificator.addRoot(root, sdkRoot.type.orderRootType)
          else modificator.removeRoot(root, sdkRoot.type.orderRootType)
        }
        modificator.commitChanges()
      }
    }
    // A module reads the roots of its interpreter from its interpreter library, which copies them from the SDK.
    syncPythonInterpreterLibraries()
    IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects()
  }

  /**
   * Parses the assertions of [text] and fails on a problem in their syntax.
   *
   * When the only problem is assertions that are not in the canonical form, the failure compares [text] with the canonical text.
   */
  private fun parseChecked(text: String): PyTestAssertionParseResult {
    val result = PyTestAssertionParser.parse(text)
    val canonicalFixes = result.diagnostics.mapNotNull { it.fix?.takeIf { fix -> fix.kind == PyTestAssertionFix.Kind.CANONICAL_FORM } }
    if (canonicalFixes.isNotEmpty() && canonicalFixes.size == result.diagnostics.size) {
      val canonicalText = canonicalFixes.sortedByDescending { it.start }.fold(text) { current, fix -> fix.applyTo(current) }
      Assertions.assertEquals(canonicalText, text, "The test assertions are not in the canonical form")
    }
    if (result.diagnostics.isNotEmpty()) {
      fail("Problems in the test assertions:\n" + result.diagnostics.joinToString("\n"))
    }
    return result
  }

  /**
   * Configures [fileContent] as the test file and puts the caret where its `CARET` marker points.
   *
   * The marker is a comment line below the code line, for example `#     └ CARET`. Use `#\ CARET` for column 0.
   * The marker line is removed, so the file holds the code alone. Call this on the EDT.
   * Pass [fileName] to configure a file other than the default test file, for example a `.pyi` stub.
   */
  protected fun configureWithCaret(
    @PyTestAssertionText @Language("Python") fileContent: String,
    fileName: String = myTestCaseOptions.testFileName,
  ): PsiFile {
    val text = fileContent.trimIndent()
    val markers = parseChecked(text).assertions.filter { it.type == PyTestDirectives.CARET.name }
    val marker = markers.singleOrNull() ?: fail("Expected one CARET marker, found ${markers.size}")
    if (marker.isInlineAssertion()) fail("The CARET marker must be on its own comment line below the code")

    val markerStart = text.lastIndexOf(NEWLINE, marker.assertionOffsetStart - 1) + 1
    val markerLineEnd = text.indexOf(NEWLINE, marker.assertionOffsetEnd)
    // The marker line comes after the code line, so its removal does not move the caret offset.
    val code = if (markerLineEnd < 0) text.substring(0, markerStart - 1) else text.removeRange(markerStart, markerLineEnd + 1)
    val file = myFixture.configureByText(fileName, code)
    myFixture.editor.caretModel.moveToOffset(marker.codeOffsetStart)
    return file
  }

  /** Runs [action] with [format] as the docstring format of the module, and restores the previous format after it. */
  protected fun withDocstringFormat(format: DocStringFormat, action: () -> Unit) {
    val settings = PyDocumentationSettings.getInstance(myFixture.module)
    val previous = settings.format
    settings.format = format
    try {
      action()
    }
    finally {
      settings.format = previous
    }
  }

  protected fun test(@PyTestAssertionText @Language("Python") fileContent: String, vararg otherFiles: Pair<String, String>) {
    runTestBody {
      doTest(fileContent, otherFiles)
    }
  }

  /**
   * Applies the quick fix named [quickFixName] and asserts that the file then reads as [expectedContent].
   *
   * The fix is looked up among the quick fixes of every issue reported in the file, so [fileContent] has to make
   * one of the enabled inspections fire. Unlike [test], [fileContent] is plain Python without inline assertions;
   * a `<caret>` marker is still honoured for fixes that need one.
   *
   * A fix that an inspection and an annotator both offer for the same code is reported once per reporter; pass
   * [expectedMatchCount] to state how many are expected, and the first of them is applied.
   */
  protected fun testQuickFix(
    @Language("Python") fileContent: String,
    quickFixName: String,
    @Language("Python") expectedContent: String,
    vararg otherFiles: Pair<String, String>,
    expectedMatchCount: Int = 1,
  ) {
    runTestBody {
      withQuickFixes(fileContent, otherFiles) { quickFixes ->
        val matching = quickFixes.filter { it.text == quickFixName }
        if (matching.size != expectedMatchCount) {
          fail("Expected $expectedMatchCount quick fix(es) named '$quickFixName', got ${quickFixes.map { it.text }}")
        }
        myFixture.launchAction(matching.first())
        myFixture.checkResult(expectedContent.trimIndent(), true)
      }
    }
  }

  /** Asserts that no quick fix named [quickFixName] is offered for any issue reported in [fileContent]. */
  protected fun assertNoQuickFix(
    @Language("Python") fileContent: String,
    quickFixName: String,
    vararg otherFiles: Pair<String, String>,
  ) {
    runTestBody {
      withQuickFixes(fileContent, otherFiles) { quickFixes ->
        val names = quickFixes.map { it.text }
        Assertions.assertFalse(quickFixName in names, "Quick fix '$quickFixName' was not expected, offered fixes: $names")
      }
    }
  }

  private fun runTestBody(body: () -> Unit) {
    // using the shared `myFixture.projectDisposable` would accumulate flag modifications
    // across all tests and dispose them only at @AfterAll, which can leave them non-nested and
    // trip RecursionManager's "Non-nested assertion flag modifications" check.
    val testDisposable = Disposer.newDisposable("PyCodeInsightTestCase test case disposable")
    if (myTestCaseOptions.assertRecursionPrevention) {
      RecursionManager.assertOnRecursionPrevention(testDisposable)
    }
    else {
      RecursionManager.disableAssertOnRecursionPrevention(testDisposable)
    }
    setRegistryKeys(myTestCaseOptions.enableRegistryKeys, myTestCaseOptions.disableRegistryKeys, testDisposable)

    try {
      setAdditionalSdkRoots(myTestCaseOptions.additionalSdkRoots, true)
      body()
    }
    finally {
      setAdditionalSdkRoots(myTestCaseOptions.additionalSdkRoots, false)
      Disposer.dispose(testDisposable)
      testCallCount++
    }
  }

  private fun doTest(fileContent: String, otherFiles: Array<out Pair<String, String>>) {
    for (copyDirectory in myTestCaseOptions.copyDirectoryToProject) {
      myFixture.copyDirectoryToProject(copyDirectory.source, copyDirectory.destination)
    }
    createOtherFiles(otherFiles)
    val originalText = fileContent.trimIndent()
    val expectedAssertions = parseChecked(originalText).assertions
    val assertions = PyTestAssertionParser.maskAssertions(originalText, expectedAssertions)
    val currentFile = myFixture.configureByText(myTestCaseOptions.testFileName, assertions)

    withInspections {
      collectAndCheckHighlighting(originalText, expectedAssertions)
    }

    if (myTestCaseOptions.assertSdkRootsNotParsed) {
      runReadActionBlocking {
        assertSdkRootsNotParsed(currentFile)
      }
    }
  }

  /** Creates or overwrites each of [otherFiles] in the source root. A file name can include folders, such as `pkg/mod.py`. */
  private fun createOtherFiles(otherFiles: Array<out Pair<String, String>>) {
    val sourceRoot = myFixture.tempDirFixture.getFile(".") ?: error("No source root")
    for ([filename, content] in otherFiles) {
      VfsTestUtil.createFile(sourceRoot, filename, content.trimIndent())
    }
  }

  private fun withQuickFixes(
    fileContent: String,
    otherFiles: Array<out Pair<String, String>>,
    check: (List<IntentionAction>) -> Unit,
  ) {
    for (copyDirectory in myTestCaseOptions.copyDirectoryToProject) {
      myFixture.copyDirectoryToProject(copyDirectory.source, copyDirectory.destination)
    }
    createOtherFiles(otherFiles)
    myFixture.configureByText(myTestCaseOptions.testFileName, fileContent.trimIndent())

    withInspections {
      check(myFixture.getAllQuickFixes())
    }
  }

  /** Runs [body] with the inspections of the test enabled: [defaultInspections] and the [TestInspections] annotation. */
  protected fun <T> withInspections(body: () -> T): T {
    val testInspections =
      defaultInspections - myTestInspections.disableInspectionsAsClasses() + myTestInspections.enableInspectionsAsClasses()

    val inspectionInstances = testInspections.map { it.getDeclaredConstructor().newInstance() }.toTypedArray()
    myFixture.enableInspections(*inspectionInstances)
    try {
      return body()
    }
    finally {
      myFixture.disableInspections(*inspectionInstances)
    }
  }

  /**
   * Asserts that the test did not load the AST of [fileName].
   * The code under test must read this file only from its stubs.
   */
  protected fun assertFileNotParsed(fileName: String) {
    runReadActionBlocking {
      val virtualFile = myFixture.findFileInTempDir(fileName) ?: error("No file $fileName")
      PyTestCase.assertNotParsed(myFixture.psiManager.findFile(virtualFile))
    }
  }

  private fun assertSdkRootsNotParsed(currentFile: PsiFile) {
    val interpreter = findPythonInterpreterForTests(myFixture.module)
    if (interpreter == null) {
      logger.warn("The interpreter is null. assertSdkRootsNotParsed is skipped")
      return
    }
    for (root in interpreter.classRoots(myFixture.project)) {
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


  private fun collectAndCheckHighlighting(expectedText: String, expectedAssertions: List<PyTestAssertion>): Duration {
    val project = myFixture.project
    runInEdtAndWait { PsiDocumentManager.getInstance(project).commitAllDocuments() }
    val file = myFixture.file as? PsiFileImpl ?: error("Expected PsiFileImpl, got ${myFixture.file?.javaClass}")
    val document = file.fileDocument

    // to load AST for changed files before it's prohibited by "fileTreeAccessFilter"
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project)

    val [highlights, duration] = measureTimedValue {
      myFixture.doHighlighting()
    }
    val actualAssertions = computeAssertions(document, highlights, expectedAssertions)

    val actualText = PyTestAssertionInliner.generateActualText(expectedText, expectedAssertions, actualAssertions)
    if (expectedText != actualText) {
      // Compare the whole fixture, so that the IDE can find the test literal and apply the diff to it.
      val message = describeSingleMismatch(expectedText, actualText, expectedAssertions, actualAssertions)
      Assertions.assertEquals(expectedText, actualText, message)
    }

    return duration
  }

  /**
   * Returns a message such as `expected <[3:0] TYPE int> but was <[3:0] TYPE str>`.
   * Returns null when the fixtures differ in more than the non-blank content of one assertion.
   */
  private fun describeSingleMismatch(
    expectedText: String,
    actualText: String,
    expectedAssertions: List<PyTestAssertion>,
    actualAssertions: List<PyTestAssertion>,
  ): String? {
    val counterparts = findCounterparts(expectedAssertions, actualAssertions)
    val [actual, expected] = counterparts.entries
                               .filter { [actual, expected] -> actual.content != expected.content }
                               .singleOrNull() ?: return null
    if (expected.content.isBlank()) return null
    val expectedPayload = PyTestAssertionSyntax.renderPayload(expected.content, expected.assertionColumnStart)
    val actualPayload = PyTestAssertionSyntax.renderPayload(actual.content, expected.assertionColumnStart)
    val idx = expectedText.indexOf(expectedPayload, expected.assertionOffsetStart)
    if (idx < 0) return null
    if (actualText != expectedText.replaceRange(idx, idx + expectedPayload.length, actualPayload)) return null
    return "expected <${expected.toString().trim()}> but was <${actual.toString().trim()}>"
  }

  private fun computeAssertions(
    document: Document,
    highlights: @Unmodifiable List<HighlightInfo>,
    expectedAssertions: List<PyTestAssertion>,
  ): List<PyTestAssertion> {
    val actualAssertions = mutableListOf<PyTestAssertion>()
    actualAssertions += createActualAssertionsForInspections(document, highlights, expectedAssertions)

    runReadActionBlocking {
      for (expectedAssertion in expectedAssertions) {
        if (expectedAssertion.isSeverity) {
          continue // actual inspection-related assertions have been added above
        }
        if (expectedAssertion.isWildcard) {
          continue // wildcard actuals are fabricated in createActualAssertionsForInspections only when a real issue matched
        }
        actualAssertions += createActualAssertionsForNonInspections(expectedAssertion)
      }
    }

    val actualAssertionsAligned = actualAssertions.toMutableList()
    val counterparts = findCounterparts(expectedAssertions, actualAssertionsAligned)
    for (idx in actualAssertionsAligned.lastIndex downTo 0) {
      val actualAssertion = actualAssertionsAligned[idx]
      val expectedAssertion = counterparts[actualAssertion] ?: continue
      if (expectedAssertion.isWildcard && !actualAssertion.isWildcard) {
        actualAssertionsAligned.removeAt(idx)
        continue
      }

      val content = if (expectedAssertion.content == "*") "*" else actualAssertion.content
      val fixmeContent = if (expectedAssertion.fixmeContent == actualAssertion.content) "You fixed it!" else expectedAssertion.fixmeContent
      val actualAssertionFixed = actualAssertion.withContent(content, fixmeContent, expectedAssertion.comment)
      actualAssertionsAligned[idx] = actualAssertionFixed
    }

    return actualAssertionsAligned
  }

  private fun createActualAssertionsForInspections(
    document: Document,
    highlights: List<HighlightInfo>,
    expectedAssertions: List<PyTestAssertion>,
  ): List<PyTestAssertion> {
    val actualAssertions = mutableListOf<PyTestAssertion>()
    val allExpectedAssertions = expectedAssertions.toMutableSet()

    for (highlight in highlights) {
      val typeName = when (highlight.severity) {
        HighlightSeverity.WARNING,
          -> if (myTestCaseOptions.enableWarnings) highlight.severity else continue
        HighlightSeverity.WEAK_WARNING,
          -> if (myTestCaseOptions.enableWeakWarnings) highlight.severity else continue
        @Suppress("DEPRECATION")
        HighlightSeverity.INFO,
        HighlightSeverity.INFORMATION,
          -> if (myTestCaseOptions.enableInfos) highlight.severity else continue
        HighlightInfoType.INJECTED_FRAGMENT_SEVERITY,
        HighlightInfoType.INJECTED_FRAGMENT_SYNTAX_SEVERITY,
          -> continue // ignore
        else -> highlight.severity
      }

      val codeLineStart = document.getLineNumber(highlight.startOffset)
      val codeColumnStart = highlight.startOffset - document.getLineStartOffset(codeLineStart)
      val codeLineEnd = document.getLineNumber(highlight.endOffset)
      val codeColumnEnd = highlight.endOffset - document.getLineStartOffset(codeLineEnd)
      val actualAssertionNoContent = PyTestAssertion(codeOffsetStart = highlight.startOffset,
                                                     codeLineStart = codeLineStart,
                                                     codeColumnStart = codeColumnStart,
                                                     codeColumnEnd = codeColumnEnd,
                                                     type = typeName.name.replace(" ", "_"),
                                                     content = "")

      val counterparts = findCounterparts(expectedAssertions, listOf(actualAssertionNoContent))
      val expectedAssertion = counterparts[actualAssertionNoContent]
      val newContent = when {
        expectedAssertion == null
          -> highlight.description
        expectedAssertion.content.split(" ").firstOrNull() == PyTestDirectives.TOOLTIP.name
          -> toolTipAssertionContent(expectedAssertion, highlight)
        else -> highlight.description
      }
      val actualAssertion = actualAssertionNoContent.copy(content = newContent ?: "")
      actualAssertions.add(actualAssertion)
      allExpectedAssertions.remove(expectedAssertion)
    }

    // adds: x = "s" # WARNING FIXME expect new issue
    for (expectedButMissingAssertion in allExpectedAssertions) {
      if (expectedButMissingAssertion.isSeverity && expectedButMissingAssertion.hasFixme && expectedButMissingAssertion.content.isBlank()) {
        actualAssertions.add(expectedButMissingAssertion.copy())
      }
    }

    // adds: x = "s" # ISSUES *
    for (expectedAssertion in expectedAssertions) {
      if (expectedAssertion.isWildcard && expectedAssertion !in allExpectedAssertions) {
        actualAssertions.add(expectedAssertion.copy())
      }
    }

    return actualAssertions
  }

  private fun toolTipAssertionContent(expectedAssertion: PyTestAssertion, highlight: HighlightInfo): String {
    val toolTip = highlight.toolTip ?: return "${PyTestDirectives.TOOLTIP.name} No tooltip on the matched highlight"
    val payload = expectedAssertion.content.removePrefix("${PyTestDirectives.TOOLTIP.name} ")
    val expectsHtml = "</" in payload || "/>" in payload
    val plainDescription = normalizeExplanationText(highlight.description)
    val toolTipText = if (expectsHtml) toolTip else toolTipToPlainText(toolTip)
    val plainToolTipText = normalizeExplanationText(toolTipText)
    val onlyToolTip = plainToolTipText.removePrefix(plainDescription)
    val fragments = payload.split("\\n").map { normalizeExplanationText(it) }

    for (fragment in fragments) {
      if (!onlyToolTip.contains(fragment)) return "${PyTestDirectives.TOOLTIP.name} $onlyToolTip"
    }
    return expectedAssertion.content
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

    if (parent is PyStringLiteralExpression && parent.findParentOfType<PyAnnotation>() != null) {
      val offsetStart = expectedAssertion.codeOffsetStart - parent.textRange.startOffset - 1
      val strLitValue = parent.stringValue
      val syntheticElement = PyUtil.createExpressionFromFragment(strLitValue, parent)
                             ?: throw AssertionError("Expression not found in string literal '$strLitValue' at pos $offsetStart")
      parent = PsiTreeUtil.getParentOfType(syntheticElement.findElementAt(offsetStart), PyExpression::class.java)
               ?: throw AssertionError("Expression not found in string literal '$strLitValue' at pos $offsetStart")
    }

    val actualContent = when (expectedAssertion.directive) {
      PyTestDirectives.TYPE -> assertType(expectedAssertion, parent)
      PyTestDirectives.EXPECTED_TYPE -> assertExpectedType(parent)
      PyTestDirectives.IS_BUILTIN -> assertIsBuiltin(parent)
      PyTestDirectives.EXPECTED_VARIANCE -> assertExpectedVariance(parent)
      PyTestDirectives.INFERRED_VARIANCE -> assertInferredVariance(parent)
      PyTestDirectives.ISSUES -> expectedAssertion.content
      PyTestDirectives.CARET -> expectedAssertion.content
      else -> "Unknown assertion type: ${expectedAssertion.type}"
    }

    val fixmeContent = if (expectedAssertion.fixmeContent == actualContent) "You fixed it!" else expectedAssertion.fixmeContent
    val actualAssertion = expectedAssertion.withContent(actualContent, fixmeContent)
    return actualAssertion
  }

  /** Asserts the declared or inferred type of a given typable element. */
  private fun assertType(expectedAssertion: PyTestAssertion, elem: PsiElement): String {
    val expr = elem as? PyTypedElement ?: elem.findParentOfType<PyTypedElement>()
    if (expr == null) {
      return "Expression not found for assertion: $expectedAssertion"
    }

    val project = expr.project
    val containingFile = expr.containingFile

    fun renderType(context: TypeEvalContext) =
      PythonDocumentationProvider.getTypeName(expr.getType(context), context)

    val actualTypeCA = renderType(codeAnalysis(project, containingFile).withTracing())
    val actualTypeUI = renderType(userInitiated(project, containingFile).withTracing())

    if (actualTypeCA != actualTypeUI) {
      return "Type mismatch for code analysis context ('$actualTypeCA') and user initiated context ('$actualTypeUI')"
    }

    return actualTypeCA
  }

  /**
   * Asserts the expected type based on a given location in the AST.
   * In the example `a: int = x` x is expected to be `int` or a subtype.
   *
   * @see [getExpectedType]
   */
  private fun assertExpectedType(elem: PsiElement): String {
    val expr = elem as? PyExpression ?: elem.findParentOfType<PyExpression>()
    if (expr == null) {
      return "Expression not found for expected type assertion"
    }
    val context = codeAnalysis(expr.project, expr.containingFile).withTracing()
    return PythonDocumentationProvider.getTypeName(getExpectedType(expr, context), context)
  }

  /** Asserts whether a given element is a builtin or not. */
  private fun assertIsBuiltin(element: PsiElement): String {
    val isBuiltin = PyBuiltinCache.getInstance(element).isBuiltin(element)
    return if (isBuiltin) "" else "FALSE"
  }

  /**
   * Asserts the expected variance based on a given location in the AST.
   *
   * @see [getExpectedVariance]
   */
  private fun assertExpectedVariance(element: PsiElement): String {
    val context = userInitiated(element.project, element.containingFile)
    if (element !is PyReferenceExpression) {
      return "Expected variance only available for PyReferenceExpressions"
    }
    val actualVariance = getExpectedVariance(element, context)
    return actualVariance.name
  }

  /**
   * Asserts the declared or inferred variance based on a given typable element,
   * usually a type variable.
   *
   * @see [getDeclaredOrInferredVariance]
   */
  private fun assertInferredVariance(element: PsiElement): String {
    val context = userInitiated(element.project, element.containingFile)
    if (element !is PyTypedElement) {
      return "Inferred variance only available for PyTypedElements"
    }
    val actualVariance = getDeclaredOrInferredVariance(element, context)
    return actualVariance?.name ?: "Unknown"
  }

  /** Strips HTML tags, unescapes entities, and collapses whitespace to render a tooltip as one line of plain text. */
  private fun toolTipToPlainText(toolTip: String): String {
    return StringUtil.collapseWhiteSpace(StringUtil.removeHtmlTags(toolTip))
  }

  /** Removes quote-like characters that differ between the description-style test fragments and the tooltip's markup. */
  private fun normalizeExplanationText(text: String): String {
    return text.replace("'", "").replace("`", "").trim()
  }
}

@Subsystems.CodeInsight
@Layers.Functional
class PyCodeInsightTestOptionsTest {

  @Test
  fun `default to latest language level`() {
    val actualLatestLL = LanguageLevel.getLatest()
    val testCaseOptionsLL = PyCodeInsightTestCase.TestCaseOptions().languageLevel
    Assertions.assertEquals(actualLatestLL, testCaseOptionsLL)
  }
}


/**
 * Verifies, through the real [PyCodeInsightTestCase.test] pipeline, that [PyCodeInsightTestCase]'s
 * `computeAssertions` correctly renders and matches assertions against real highlighting and real
 * type inference (as opposed to the parser/inliner unit tests above, which fabricate their own data).
 */
@Subsystems.CodeInsight
@Layers.Functional
class PyCodeInsightTestCaseComputeAssertionsTest : PyCodeInsightTestCase() {

  @Test
  fun `WARNING inline matching message passes`() = test("""
    i: int = "" # WARNING Expected type 'int', got 'Literal[""]' instead
    """)

  @Test
  fun `WARNING inline wrong message fails`() {
    Assertions.assertThrows(AssertionError::class.java) {
      test("""
        i: int = "" # WARNING Wrong warning message
        """)
    }
  }

  @Test
  fun `WARNING inline missing warning fails`() {
    Assertions.assertThrows(AssertionError::class.java) {
      test("""
        x = 1 # WARNING No warning message
        """)
    }
  }

  @Test
  fun `WARNING inline FIXME placeholder without actual warning passes`() = test("""
    x = 1 # WARNING FIXME some future warning
    """)

  @Test
  fun `WARNING inline FIXME wrong expectation fails`() {
    Assertions.assertThrows(AssertionError::class.java) {
      test("""
        i: int = "" # WARNING Wrong warning message FIXME
        """)
    }
  }

  @Test
  fun `ISSUES inline wildcard matching issue passes`() = test("""
    i: int = "" # ISSUES *
    """)

  @Test
  fun `ISSUES inline wildcard matching rest issue passes 1`() = test("""
    x: int = "s" + 1 # ISSUES * # Covers issue "No overload of '__add__' matches the arguments. Argument types: (Literal[1]). Expected one of: (value: LiteralString), (value: str)"
    #        ^^^^^^^ WARNING Expected type 'int', got 'UnsafeUnion[LiteralString, str] | int' instead
    """)

  @Test
  fun `ISSUES inline wildcard matching rest issue passes 2`() = test("""
    x: int = "s" + 1 # ISSUES * # Covers issue "Expected type 'int', got 'UnsafeUnion[LiteralString, str] | int' instead"
    #              └ WARNING No overload of '__add__' matches the arguments. Argument types: (Literal[1]). Expected one of: (value: LiteralString), (value: str)
    """)

  @Test
  fun `ISSUES inline wildcard without issue fails`() {
    Assertions.assertThrows(AssertionError::class.java) {
      test("""
        i: int = 2 # ISSUES *
        """)
    }
  }

  @Test
  fun `WARNING comment-marker FIXME placeholder without actual warning passes`() = test("""
    x = 1
    #   └ WARNING FIXME some future warning
    """)

  @Test
  fun `WARNING comment-marker FIXME wrong expectation fails`() {
    Assertions.assertThrows(AssertionError::class.java) {
      test("""
        x = 1
        #   └ WARNING No warning message FIXME some future warning
        """)
    }
  }

  @Test
  fun `TYPE inline matching type passes`() = test("""
    s = "" # TYPE Literal[""]
    """)

  @Test
  fun `TYPE comment-marker matching type passes`() = test("""
    n = 1
    #   └ TYPE Literal[1]
    """)

  @Test
  fun `TYPE inline wrong type fails`() {
    Assertions.assertThrows(AssertionError::class.java) {
      test("""
        n = 1 # TYPE str
        """)
    }
  }

  @Test
  fun `TYPE inline FIXME current and future type passes`() = test("""
    n = 1 # TYPE Literal[1] FIXME str
    """)

  @Test
  fun `TYPE indented comment-marker matching type passes`() = test("""
    def f():
        n = 1
        #│  └ TYPE Literal[1]
        #\ TYPE Literal[1]
    """)

  @Test
  fun `TYPE inline after a hash in a string passes`() = test("""
    s = "#" # TYPE Literal["\#"]
    """)

  @Test
  fun `registry options enable a key and disable a key until the test ends`(@TestDisposable testDisposable: Disposable) {
    val keysDisposable = Disposer.newDisposable(testDisposable, "registry keys")
    setRegistryKeys(
      enable = arrayOf("python.parameter.info.show.all.hints"),
      disable = arrayOf("python.type.checker.diff.tooltip"),
      parentDisposable = keysDisposable,
    )
    Assertions.assertTrue(Registry.`is`("python.parameter.info.show.all.hints"))
    Assertions.assertFalse(Registry.`is`("python.type.checker.diff.tooltip"))

    Disposer.dispose(keysDisposable)
    Assertions.assertFalse(Registry.`is`("python.parameter.info.show.all.hints"))
    Assertions.assertTrue(Registry.`is`("python.type.checker.diff.tooltip"))
  }
}

/**
 * Verifies that within a single subclass of [PyCodeInsightTestCase], two consecutive test
 * methods observe:
 *  1. The very same [myFixture] instance (it is re-used across the methods of the subclass),
 *  2. A re-initialized per-test state (open editors closed, temp dir wiped, [testCallCount] reset).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Subsystems.CodeInsight
@Layers.Functional
class PyCodeInsightTestCaseFixtureReuseTest : PyCodeInsightTestCase() {

  companion object {
    private var fixtureFromFirstTest: CodeInsightTestFixture? = null
    private var projectFromFirstTest: Project? = null
    private var tempDirFromFirstTest: TempDirTestFixture? = null
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

/**
 * Verifies that an option that a subclass adds to [PyCodeInsightTestCase.fixtureKey] gives a test a new fixture, and that
 * the options of the superclass stay in the key.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Subsystems.CodeInsight
@Layers.Functional
@TestFor(classes = [PyCodeInsightTestCase::class])
class PyCodeInsightTestCaseFixtureKeyTest : PyCodeInsightTestCase() {

  @Target(AnnotationTarget.FUNCTION)
  @Retention(AnnotationRetention.RUNTIME)
  annotation class Flavor(val name: String)

  override fun fixtureKey(testCaseOptions: TestCaseOptions): List<Any?> =
    super.fixtureKey(testCaseOptions) + findTestAnnotation(Flavor::class.java)?.name

  companion object {
    private var previousFixture: CodeInsightTestFixture? = null
  }

  @Test
  @Order(1)
  @Flavor("first")
  fun `the first flavor sets up a fixture`() {
    previousFixture = myFixture
  }

  @Test
  @Order(2)
  @Flavor("first")
  fun `the same flavor keeps the fixture`() {
    Assertions.assertSame(previousFixture, myFixture)
  }

  @Test
  @Order(3)
  @Flavor("second")
  fun `another flavor sets up a new fixture`() {
    Assertions.assertNotNull(previousFixture, "The first test must run before this one")
    Assertions.assertNotSame(previousFixture, myFixture)
    previousFixture = myFixture
  }

  @Test
  @Order(4)
  @Flavor("second")
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `another language level with the same flavor sets up a new fixture`() {
    Assertions.assertNotNull(previousFixture, "The third test must run before this one")
    Assertions.assertNotSame(previousFixture, myFixture)
  }
}

/** Verifies that [PyCodeInsightTestCase.findTestAnnotation] reads the class annotation of the running test class. */
@Subsystems.CodeInsight
@Layers.Functional
@TestFor(classes = [PyCodeInsightTestCase::class])
open class PyCodeInsightTestCaseClassAnnotationTest : PyCodeInsightTestCase() {

  @Target(AnnotationTarget.CLASS)
  @Retention(AnnotationRetention.RUNTIME)
  annotation class Mark(val name: String)

  protected open val expectedMark: String? = null

  @Test
  fun `the annotation of the running test class is found for an inherited test method`() {
    Assertions.assertEquals(expectedMark, findTestAnnotation(Mark::class.java)?.name)
  }
}

@Subsystems.CodeInsight
@Layers.Functional
@TestFor(classes = [PyCodeInsightTestCase::class])
@PyCodeInsightTestCaseClassAnnotationTest.Mark("subclass")
class PyCodeInsightTestCaseSubclassAnnotationTest : PyCodeInsightTestCaseClassAnnotationTest() {
  override val expectedMark: String = "subclass"
}
