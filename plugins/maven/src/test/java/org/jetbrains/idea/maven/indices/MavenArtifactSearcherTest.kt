// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.maven.testFramework.fixtures.MavenIndicesTestFixture
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertUnorderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenArtifactSearcherTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private val JUNIT_VERSIONS = arrayOf("junit:junit:4.0", "junit:junit:3.8.2", "junit:junit:3.8.1")
  private val JMOCK_VERSIONS = arrayOf("jmock:jmock:1.2.0", "jmock:jmock:1.1.0", "jmock:jmock:1.0.0")
  private val COMMONS_IO_VERSIONS = arrayOf("commons-io:commons-io:2.4")

  private lateinit var myIndicesFixture: MavenIndicesTestFixture

  @Throws(Exception::class)
  @BeforeEach
  fun setUp() {
    runBlocking(Dispatchers.EDT) {
      writeIntentReadAction {
        myIndicesFixture = MavenIndicesTestFixture(maven.dir, maven.project, maven.testRootDisposable)
        myIndicesFixture.setUp()
      }
    }
  }

  @AfterEach
  fun tearDown() = runBlocking(Dispatchers.EDT) {
    myIndicesFixture.tearDown()
  }

  @Test
  fun `artifact search - empty`() = assertArtifactSearchResults("")

  @Test
  fun `artifact search - j colon j`() = assertArtifactSearchResults("j:j", *(JMOCK_VERSIONS + JUNIT_VERSIONS))

  @Test
  fun `artifact search - junit`() = assertArtifactSearchResults("junit", *JUNIT_VERSIONS)

  @Test
  fun `artifact search - junit space version prefix`() = assertArtifactSearchResults("junit 3.", *JUNIT_VERSIONS)

  @Test
  fun `artifact search - uni partial no match`() = assertArtifactSearchResults("uni 3.")

  @Test
  fun `artifact search - two partial words no match`() = assertArtifactSearchResults("juni juni 3.")

  @Test
  fun `artifact search - junit with extra keyword`() = assertArtifactSearchResults("junit foo", *JUNIT_VERSIONS)

  @Test
  fun `artifact search - typo groupId colon artifactId colon version`() = assertArtifactSearchResults("juni:juni:3.", *JUNIT_VERSIONS)

  @Test
  fun `artifact search - groupId colon`() = assertArtifactSearchResults("junit:", *JUNIT_VERSIONS)

  @Test
  fun `artifact search - groupId colon artifactId`() = assertArtifactSearchResults("junit:junit", *JUNIT_VERSIONS)

  @Test
  fun `artifact search - groupId colon artifactId colon version prefix`() = assertArtifactSearchResults("junit:junit:3.", *JUNIT_VERSIONS)

  @Test
  fun `artifact search - groupId colon artifactId colon exact version`() = assertArtifactSearchResults("junit:junit:4.0", *JUNIT_VERSIONS)

  @Test
  fun `artifact search - hyphenated artifact`() = assertArtifactSearchResults("commons", *COMMONS_IO_VERSIONS)

  @Test
  fun `artifact search - hyphenated artifact with dash`() = assertArtifactSearchResults("commons-", *COMMONS_IO_VERSIONS)

  @Test
  fun `artifact search - hyphenated artifact full name`() = assertArtifactSearchResults("commons-io", *COMMONS_IO_VERSIONS)

  private fun assertArtifactSearchResults(pattern: String, vararg expected: String) {
    val actual: MutableList<String> = ArrayList()
    runBlocking {
      for (eachResult in MavenArtifactSearcher().search(maven.project, pattern, 100)) {
        for (eachVersion in eachResult.searchResults.items) {
          actual.add("${eachVersion.groupId}:${eachVersion.artifactId}:${eachVersion.version}")
        }
      }
    }
    assertUnorderedElementsAreEqual(actual, *expected)
  }
}
