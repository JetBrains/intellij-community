// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.platform.uast.testFramework.common.PsiClassToString
import com.intellij.platform.uast.testFramework.common.UastMappingsAccountantTest
import com.intellij.platform.uast.testFramework.common.UastMappingsAccountantTestBase
import com.intellij.platform.uast.testFramework.common.sourcesFromDirRecursive
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File


/**
 * Computes Java PSI to UAST mappings over the [javaUastTestDataPath]
 */
@Disabled("Not a test suite, should be called manually only")
@TestApplication
class JavaUastMappingsAccountantTest : UastMappingsAccountantTestBase {
  companion object {
    private val LOG = Logger.getInstance(JavaUastMappingsAccountantTest::class.java)

    private val projectFixture = lightJavaProjectFixture()
  }

  private val uast by javaUastFixture(projectFixture)

  private val javaFileMatcher = ExtensionFileNameMatcher(JavaFileType.INSTANCE.defaultExtension)

  private val delegate by lazy(LazyThreadSafetyMode.NONE) {
    File(uast.fixture.testDataPath).let { testData ->
      UastMappingsAccountantTest(
        sources = sourcesFromDirRecursive(testData, javaFileMatcher, uast.fixture).asIterable(),
        storeResultsTo = testData.absoluteFile.parentFile.toPath(),
        resultsNamePrefix = "java-mappings",
        psiClassPrinter = PsiClassToString.asClosestInterface,
        logger = LOG
      )
    }
  }

  @Test
  override fun `test compute mappings by PSI element and print as trees oriented as child to parent`(): Unit =
    delegate.`test compute mappings by PSI element and print as trees oriented as child to parent`()

  @Test
  override fun `test compute mappings by PSI element and print as trees oriented as parent to child`(): Unit =
    delegate.`test compute mappings by PSI element and print as trees oriented as parent to child`()

  @Test
  override fun `test compute mappings by PSI element and print as lists oriented as child to parent`(): Unit =
    delegate.`test compute mappings by PSI element and print as lists oriented as child to parent`()

  @Test
  override fun `test compute mappings by PSI element and print as lists oriented as parent to child`(): Unit =
    delegate.`test compute mappings by PSI element and print as lists oriented as parent to child`()

  @Test
  override fun `test compute mappings by UAST element and print as trees oriented as child to parent`(): Unit =
    delegate.`test compute mappings by UAST element and print as trees oriented as child to parent`()

  @Test
  override fun `test compute mappings by UAST element and print as trees oriented as parent to child`(): Unit =
    delegate.`test compute mappings by UAST element and print as trees oriented as parent to child`()

  @Test
  override fun `test compute mappings by UAST element and print as lists oriented as child to parent`(): Unit =
    delegate.`test compute mappings by UAST element and print as lists oriented as child to parent`()

  @Test
  override fun `test compute mappings by UAST element and print as lists oriented as parent to child`(): Unit =
    delegate.`test compute mappings by UAST element and print as lists oriented as parent to child`()

  @Test
  override fun `test compute mappings by UAST element and print as a priory lists of PSI elements`(): Unit =
    delegate.`test compute mappings by UAST element and print as a priory lists of PSI elements`()
}