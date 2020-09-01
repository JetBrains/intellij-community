// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.PsiClassToString
import org.jetbrains.uast.test.common.UastMappingsAccountantTest
import org.jetbrains.uast.test.common.UastMappingsAccountantTestBase
import org.jetbrains.uast.test.common.sourcesFromDirRecursive
import org.junit.Ignore
import org.junit.Test
import java.io.File


/**
 * Computes Java PSI to UAST mappings over the [AbstractJavaUastTest.getTestDataPath]
 */
@Ignore("Not a test suite, should be called manually only")
class JavaUastMappingsAccountantTest :
  AbstractJavaUastTest(),
  UastMappingsAccountantTestBase {

  override fun check(testName: String, file: UFile) = throw AssertionError("Must not be called")

  private val javaFileMatcher = ExtensionFileNameMatcher(JavaFileType.INSTANCE.defaultExtension)

  private val delegate by lazy(LazyThreadSafetyMode.NONE) {
    File(testDataPath).let { testData ->
      UastMappingsAccountantTest(
        sources = sourcesFromDirRecursive(testData, javaFileMatcher, myFixture).asIterable(),
        storeResultsTo = testData.absoluteFile.parentFile.toPath(),
        resultsNamePrefix = "java-mappings",
        psiClassPrinter = PsiClassToString.asClosestInterface,
        logger = LOG
      )
    }
  }

  @Test
  override fun `test compute mappings by PSI element and print as trees oriented as child to parent`() =
    delegate.`test compute mappings by PSI element and print as trees oriented as child to parent`()

  @Test
  override fun `test compute mappings by PSI element and print as trees oriented as parent to child`() =
    delegate.`test compute mappings by PSI element and print as trees oriented as parent to child`()

  @Test
  override fun `test compute mappings by PSI element and print as lists oriented as child to parent`() =
    delegate.`test compute mappings by PSI element and print as lists oriented as child to parent`()

  @Test
  override fun `test compute mappings by PSI element and print as lists oriented as parent to child`() =
    delegate.`test compute mappings by PSI element and print as lists oriented as parent to child`()

  @Test
  override fun `test compute mappings by UAST element and print as trees oriented as child to parent`() =
    delegate.`test compute mappings by UAST element and print as trees oriented as child to parent`()

  @Test
  override fun `test compute mappings by UAST element and print as trees oriented as parent to child`() =
    delegate.`test compute mappings by UAST element and print as trees oriented as parent to child`()

  @Test
  override fun `test compute mappings by UAST element and print as lists oriented as child to parent`() =
    delegate.`test compute mappings by UAST element and print as lists oriented as child to parent`()

  @Test
  override fun `test compute mappings by UAST element and print as lists oriented as parent to child`() =
    delegate.`test compute mappings by UAST element and print as lists oriented as parent to child`()

  @Test
  override fun `test compute mappings by UAST element and print as a priory lists of PSI elements`() =
    delegate.`test compute mappings by UAST element and print as a priory lists of PSI elements`()
}