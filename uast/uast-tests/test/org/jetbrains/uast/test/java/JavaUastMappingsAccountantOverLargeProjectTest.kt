// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.ide.highlighter.JavaFileType
import org.jetbrains.uast.test.common.PsiClassToString
import org.jetbrains.uast.test.common.UastMappingsAccountantSingleTestBase
import org.jetbrains.uast.test.common.UastMappingsAccountantTest
import org.jetbrains.uast.test.common.sourcesFromLargeProject
import org.jetbrains.uast.test.env.AbstractLargeProjectTest
import org.junit.Ignore
import org.junit.Test
import java.nio.file.Path


/**
 * Computes Java PSI to Uast mappings over the custom large project.
 *
 * It is recommended to clone Intellij Community repo (with `git clone --depth 1`)
 * and specify the [testProjectPath] to it.
 * Or you can run it on the very same repo you are working from,
 * but be careful -- some *.iml files may then be updated, revert them.
 *
 * If you want to open the project (like Intellij Ultimate or Kotlin IDE)
 * with kotlin-facet required, you should add Kotlin as the library
 * via [AbstractLargeProjectTest.projectLibraries].
 */
@Ignore("Very laborious task, should be invoked manually only")
class JavaUastMappingsAccountantOverLargeProjectTest :
  AbstractLargeProjectTest(), UastMappingsAccountantSingleTestBase {

  override val testProjectPath: Path = throw NotImplementedError("Must be specified manually")

  private val sourcesToProcessLimit = 50000 // will require up to 4 hours to finish

  private val delegate by lazy(LazyThreadSafetyMode.NONE) {
    UastMappingsAccountantTest(
      sources = sourcesFromLargeProject(JavaFileType.INSTANCE, project, sourcesToProcessLimit, LOG).asIterable(),
      storeResultsTo = testProjectPath,
      resultsNamePrefix = "FULL-java-mappings",
      psiClassPrinter = PsiClassToString.asClosestInterface,
      doInParallel = true
    )
  }

  @Test
  override fun `test compute all mappings and print in all variations`() {
    delegate.`test compute all mappings and print in all variations`()
  }
}