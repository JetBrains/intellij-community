// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.fixtures

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import java.util.function.Function

/**
 * JUnit 5 test fixture that hosts a [CodeInsightTestFixture] over a real Maven project, providing the
 * completion / reference-resolution / highlighting helpers that the legacy `MavenDomWithIndicesTestCase`
 * hierarchy used to offer, without extending it.
 *
 * Obtain it via [mavenDomFixture] using the property-delegate pattern:
 * ```
 * @TestApplication
 * class MyMavenCompletionTest {
 *   private val maven by mavenDomFixture(withIndices = true)
 *
 *   @Test
 *   fun test() = runBlocking {
 *     maven.updateProjectPom("<groupId>test</groupId><artifactId>project</artifactId><version>1</version>")
 *     maven.assertCompletionVariants(maven.projectPom, "...")
 *   }
 * }
 * ```
 *
 * This class is intentionally thin: it owns the per-test state and lifecycle only. The completion,
 * reference-resolution, highlighting, rename, import, versioning and module helpers are provided as
 * extension functions.
 *
 * It is created by [mavenDomFixture] over a per-method [Project] supplied by the platform `projectFixture`, with the
 * [CodeInsightTestFixture] attached (set up) **before** the initial Maven import so that its
 * `VirtualFilePointerTracker` baseline is established before the import creates project-scoped pointers. A fresh project
 * is created and disposed for every test method.
 *
 * When [indices] is non-null, copies the local and extra test repositories
 * into a temp dir, points Maven's local repository at it, and builds GAV indices so that completion and
 * resolution of repository artifacts work offline.
 */
interface MavenDomTestFixture : MavenImportingTestFixture {
  val fixture: CodeInsightTestFixture
  val repositoryHelper: MavenCustomRepositoryHelper
  val configTimestamps: MutableMap<VirtualFile, Long>

  val indices: MavenDomTestFixtureIndices?

  @Suppress("PropertyName")
  val RENDERING_TEXT: Function<LookupElement, String?>
    get() = Function { li: LookupElement ->
      val presentation = LookupElementPresentation()
      li.renderElement(presentation)
      presentation.itemText
    }

  @Suppress("PropertyName")
  val LOOKUP_STRING: Function<LookupElement, String?>
    get() = Function { obj: LookupElement -> obj.lookupString }

  class Highlight(
    val severity: HighlightSeverity = HighlightSeverity.ERROR,
    val text: String? = null,
    val description: String? = null,
  ) {
    fun matches(info: HighlightInfo): Boolean {
      return severity == info.severity &&
             (text == null || text == info.text) &&
             (description == null || description == info.description)
    }

    override fun toString(): String = "Highlight(severity=$severity, text=$text, description=$description)"
  }

  companion object {
    @Language("XML")
    val DEFAULT_POM: String = """
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
    """
  }
}

data class MavenDomTestFixtureIndices(val localRepoDir: String, val extraRepoDirs: List<String>)