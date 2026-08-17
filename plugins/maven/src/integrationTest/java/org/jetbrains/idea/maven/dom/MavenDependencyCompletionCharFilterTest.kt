// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.configTest
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.SERVER
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * IDEA-392103: typing a character that occurs inside a dependency coordinate must not finish the lookup and truncate
 * the already typed text. See [com.intellij.maven.completion.MavenCompletionCharFilter].
 */
@TestApplication
class MavenDependencyCompletionCharFilterTest {

  private val maven by mavenDomFixture(initialPom = MavenDomTestFixture.DEFAULT_POM)

  @TestDisposable
  private lateinit var disposable: Disposable

  private val testCompletionService = object : DependencyCompletionService {
    override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> =
      flowOf(DependencyCompletionEvent.Item(DependencyCompletionResult("org.springframework", "spring-core", "6.2.0", source = SERVER)))

    override fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
      flowOf(DependencyCompletionEvent.Item(DependencyPartCompletionResult("org.springframework", SERVER)))

    override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
      flowOf(DependencyCompletionEvent.Item(DependencyPartCompletionResult("spring-core.suffix", SERVER)))

    override fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
      flowOf(DependencyCompletionEvent.Item(DependencyPartCompletionResult("6.2.0", SERVER)))
  }

  @BeforeEach
  fun replaceCompletionService() {
    application.replaceService(DependencyCompletionService::class.java, testCompletionService, disposable)
  }

  @ParameterizedTest
  @ValueSource(chars = ['-', ':', '.', '_'])
  fun `test typing special char in top level does not truncate typed text`(charToType: Char) = runBlocking {
    completeAndType("""
      <dependencies>
        org.springframework<caret>
      </dependencies>
      """, charToType)

    assertTrue(documentText.contains("org.springframework$charToType"),
               "The typed text must be preserved and extended, but was:\n$documentText")
    assertNotNull(activeLookup, "'$charToType' must be added to the prefix, so the lookup must stay open")
  }

  @ParameterizedTest
  @ValueSource(chars = ['-', ':', '.', '_'])
  fun `test typing special char in dependency tag does not truncate typed text`(charToType: Char) = runBlocking {
    completeAndType("""
      <dependencies>
        <dependency>org.springframework<caret></dependency>
      </dependencies>
      """, charToType)

    assertTrue(documentText.contains("<dependency>org.springframework$charToType</dependency>"),
               "The typed text must be preserved and extended, but was:\n$documentText")
    assertNotNull(activeLookup, "'$charToType' must be added to the prefix, so the lookup must stay open")
  }

  @ParameterizedTest
  @ValueSource(chars = ['-', '.', '_'])
  fun `test typing special char in group id tag does not truncate typed text`(charToType: Char) = runBlocking {
    completeAndType("""
      <dependencies>
        <dependency>
          <groupId>org.springframework<caret></groupId>
        </dependency>
      </dependencies>
      """, charToType)

    assertTrue(documentText.contains("<groupId>org.springframework$charToType</groupId>"),
               "The typed text must be preserved and extended, but was:\n$documentText")
    assertNotNull(activeLookup, "'$charToType' must be added to the prefix, so the lookup must stay open")
  }

  @ParameterizedTest
  @ValueSource(chars = ['-', '.', '_'])
  fun `test typing special char in artifact id tag does not truncate typed text`(charToType: Char) = runBlocking {
    completeAndType("""
      <dependencies>
        <dependency>
          <groupId>org.springframework</groupId>
          <artifactId>spring-core<caret></artifactId>
        </dependency>
      </dependencies>
      """, charToType)

    assertTrue(documentText.contains("<artifactId>spring-core$charToType</artifactId>"),
               "The typed text must be preserved and extended, but was:\n$documentText")
    assertNotNull(activeLookup, "'$charToType' must be added to the prefix, so the lookup must stay open")
  }

  @Test
  fun `test typing dot in version tag keeps platform behaviour`() = runBlocking {
    completeAndType("""
      <dependencies>
        <dependency>
          <groupId>org.springframework</groupId>
          <artifactId>spring-core</artifactId>
          <version>6.<caret></version>
        </dependency>
      </dependencies>
      """, '.')

    assertTrue(documentText.contains("<version>6.2.</version>"),
               "The version lookup must keep completing till the typed char occurrence, but was:\n$documentText")
  }

  private suspend fun completeAndType(
    @Language(value = "XML", prefix = "<project>", suffix = "</project>") dependencies: String,
    c: Char,
  ) {
    maven.updateProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      ${dependencies.trimIndent()}
      """.trimIndent())
    maven.configTest(maven.projectPom)
    withContext(Dispatchers.EDT) {
      val lookupElements = maven.fixture.complete(CompletionType.BASIC)
      assertTrue(!lookupElements.isNullOrEmpty(), "The fake completion service must have produced lookup elements")
      assertEquals(true, (activeLookup as? LookupImpl)?.isFocused, "An explicitly invoked lookup must be focused")
      maven.fixture.type(c)
    }
  }

  private val activeLookup
    get() = LookupManager.getActiveLookup(maven.fixture.editor)

  private val documentText
    get() = maven.fixture.editor.document.text
}
