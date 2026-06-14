/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.indices

import com.intellij.maven.testFramework.fixtures.MavenIndicesTestFixture
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertOrderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import org.jetbrains.idea.maven.indices.searcher.MavenLuceneIndexer
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenSearcherTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private lateinit var myIndicesFixture: MavenIndicesTestFixture
  private lateinit var myRepo: MavenRepositoryInfo


  @Throws(Exception::class)
  @BeforeEach
  fun setUp() {
    runBlocking(Dispatchers.EDT) {
      writeIntentReadAction {
        myIndicesFixture = MavenIndicesTestFixture(maven.dir, maven.project, maven.testRootDisposable)
        myIndicesFixture.setUp()
      }
    }
    runBlocking {
      MavenSystemIndicesManager.getInstance().waitAllGavsUpdatesCompleted()
      myRepo = MavenIndexUtils.getLocalRepository(maven.project)!!
      MavenLuceneIndexer.getInstance().update(listOf(myRepo), true)
      MavenSystemIndicesManager.getInstance().waitAllLuceneUpdatesCompleted()
    }
  }

  @AfterEach
  fun tearDown() = runBlocking(Dispatchers.EDT) {
    myIndicesFixture.tearDown()
  }

  @Test
  fun testClassSearch() = runBlocking(Dispatchers.EDT) {
    assertClassSearchResults("TestCas",
                             "TestCase(junit.framework) junit:junit:4.0 junit:junit:3.8.2 junit:junit:3.8.1",
                             "TestCaseClassLoader(junit.runner) junit:junit:3.8.2 junit:junit:3.8.1")
    assertClassSearchResults("TESTcase",
                             "TestCase(junit.framework) junit:junit:4.0 junit:junit:3.8.2 junit:junit:3.8.1",
                             "TestCaseClassLoader(junit.runner) junit:junit:3.8.2 junit:junit:3.8.1")

    assertClassSearchResults("After",
                             "After(org.junit) junit:junit:4.0",
                             "AfterClass(org.junit) junit:junit:4.0")
    assertClassSearchResults("After ",
                             "After(org.junit) junit:junit:4.0")
    assertClassSearchResults("*After",
                             "After(org.junit) junit:junit:4.0",
                             "AfterClass(org.junit) junit:junit:4.0",
                             "BeforeAndAfterRunner(org.junit.internal.runners) junit:junit:4.0",
                             "InvokedAfterMatcher(org.jmock.core.matcher) jmock:jmock:1.2.0 jmock:jmock:1.1.0 jmock:jmock:1.0.0")

    // do not include package hits 
    assertClassSearchResults("JUnit",
                             "JUnit4TestAdapter(junit.framework) junit:junit:4.0",
                             "JUnit4TestAdapterCache(junit.framework) junit:junit:4.0",
                             "JUnit4TestCaseFacade(junit.framework) junit:junit:4.0",
                             "JUnitCore(org.junit.runner) junit:junit:4.0")

    assertClassSearchResults("org.junit.After",
                             "After(org.junit) junit:junit:4.0",
                             "AfterClass(org.junit) junit:junit:4.0")

    assertClassSearchResults("org.After",
                             "After(org.junit) junit:junit:4.0",
                             "AfterClass(org.junit) junit:junit:4.0")

    assertClassSearchResults("junit.After",
                             "After(org.junit) junit:junit:4.0",
                             "AfterClass(org.junit) junit:junit:4.0")

    assertClassSearchResults("or.jun.After",
                             "After(org.junit) junit:junit:4.0",
                             "AfterClass(org.junit) junit:junit:4.0")

    // do not include other packages
    assertClassSearchResults("junit.framework.Test ",
                             "Test(junit.framework) junit:junit:4.0 junit:junit:3.8.2 junit:junit:3.8.1")

    assertClassSearchResults("!@][#$%)(^&*()_") // shouldn't throw
  }

  private suspend fun assertClassSearchResults(pattern: String, vararg expected: String) {
    assertOrderedElementsAreEqual(getClassSearchResults(pattern), *expected)
  }

  private suspend fun getClassSearchResults(pattern: String): List<String> {
    val actualArtifacts: MutableList<String> = ArrayList()
    for (eachResult in MavenLuceneIndexer.getInstance().search(pattern, listOf(myRepo), 100)) {
      val s = StringBuilder(eachResult.className + "(" + eachResult.packageName + ")")
      for (eachVersion in eachResult.searchResults.items) {
        if (s.length > 0) s.append(" ")
        s.append(eachVersion.groupId).append(":").append(eachVersion.artifactId).append(":").append(eachVersion.version)
      }
      actualArtifacts.add(s.toString())
    }
    return actualArtifacts
  }
}
