package com.jetbrains.python.fixtures

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.junit5.TestDisposable
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

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
