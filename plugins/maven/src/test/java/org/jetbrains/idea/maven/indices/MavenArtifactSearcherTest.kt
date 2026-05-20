// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenArtifactSearcherTest : MavenIndicesTestCase() {
  private val JUNIT_VERSIONS = arrayOf("junit:junit:4.0", "junit:junit:3.8.2", "junit:junit:3.8.1")
  private val JMOCK_VERSIONS = arrayOf("jmock:jmock:1.2.0", "jmock:jmock:1.1.0", "jmock:jmock:1.0.0")
  private val COMMONS_IO_VERSIONS = arrayOf("commons-io:commons-io:2.4")

  private lateinit var myIndicesFixture: MavenIndicesTestFixture

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    runBlocking(Dispatchers.EDT) {
      writeIntentReadAction {
        myIndicesFixture = MavenIndicesTestFixture(dir, project, testRootDisposable)
        myIndicesFixture.setUp()
      }
    }
  }

  @Throws(Exception::class)
  override fun tearDown() = runBlocking(Dispatchers.EDT) {
    try {
      myIndicesFixture.tearDown()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun testArtifactSearch() = runBlocking {
    assertArtifactSearchResults("")
    assertArtifactSearchResults("j:j", *(JMOCK_VERSIONS + JUNIT_VERSIONS))
    assertArtifactSearchResults("junit", *JUNIT_VERSIONS)
    assertArtifactSearchResults("junit 3.", *JUNIT_VERSIONS)
    assertArtifactSearchResults("uni 3.")
    assertArtifactSearchResults("juni juni 3.")
    assertArtifactSearchResults("junit foo", *JUNIT_VERSIONS)
    assertArtifactSearchResults("juni:juni:3.", *JUNIT_VERSIONS)
    assertArtifactSearchResults("junit:", *JUNIT_VERSIONS)
    assertArtifactSearchResults("junit:junit", *JUNIT_VERSIONS)
    assertArtifactSearchResults("junit:junit:3.", *JUNIT_VERSIONS)
    assertArtifactSearchResults("junit:junit:4.0", *JUNIT_VERSIONS)
  }

  @Test
  fun testArtifactSearchDash() = runBlocking {
    assertArtifactSearchResults("commons", *COMMONS_IO_VERSIONS)
    assertArtifactSearchResults("commons-", *COMMONS_IO_VERSIONS)
    assertArtifactSearchResults("commons-io", *COMMONS_IO_VERSIONS)
  }

  private fun assertArtifactSearchResults(pattern: String, vararg expected: String) {
    val actual: MutableList<String> = ArrayList()
    var s: StringBuilder
    for (eachResult in MavenArtifactSearcher().search(project, pattern, 100)) {
      for (eachVersion in eachResult.searchResults.items) {
        s = StringBuilder()
        s.append(eachVersion.groupId).append(":").append(eachVersion.artifactId).append(":").append(eachVersion.version)
        actual.add(s.toString())
      }
    }
    assertUnorderedElementsAreEqual(actual, *expected)
  }
}
