// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.search

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenSearchTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun `test searching POM files by module name`() = runBlocking {
    maven.createProjectPom("""<groupId>test</groupId>
                     <artifactId>p1</artifactId>
                     <packaging>pom</packaging>
                     <version>1</version>
                     <modules>
                       <module>module1</module>
                       <module>module2</module>
                     </modules>""")
    val m1File = maven.createModulePom("module1",
                    """<parent>
                      <groupId>test</groupId>
                      <artifactId>p1</artifactId>
                      <version>1</version>
                    </parent>
                    <groupId>test</groupId>
                    <artifactId>module1</artifactId>
                    <version>1</version>""")
    val m2File = maven.createModulePom("module2",
                    """<parent>
                      <groupId>test</groupId>
                      <artifactId>p1</artifactId>
                      <version>1</version>
                    </parent>
                    <groupId>test</groupId>
                    <artifactId>module2</artifactId>
                    <version>1</version>""")
    maven.importProjectAsync()

    runInEdtSmartMode {
      val m1Psi = PsiManager.getInstance(maven.project).findFile(m1File)
      val m2Psi = PsiManager.getInstance(maven.project).findFile(m2File)
      UsefulTestCase.assertContainsElements(lookForFiles("module1"), m1Psi)
      UsefulTestCase.assertContainsElements(lookForFiles("module2"), m2Psi)
    }
  }

  private fun runInEdtSmartMode(action: () -> Unit) {
    val latch = CountDownLatch(1)
    DumbService.getInstance(maven.project).smartInvokeLater {
      action()
      latch.countDown()
    }
    val actionPerformed = latch.await(1, TimeUnit.MINUTES)
    assertTrue(actionPerformed, "Action has not been performed in 1 minute")
  }

  private fun lookForFiles(pattern: String): List<Any> =
    createFileContributor()
      .apply { Disposer.register(maven.disposable, this) }
      .search(pattern, MockProgressIndicator())

  private fun createFileContributor(): SearchEverywhereContributor<Any> =
    FileSearchEverywhereContributor(createStubEvent())

  private fun createStubEvent(): AnActionEvent {
    val dataContext = SimpleDataContext.getProjectContext(maven.project)
    return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext)
  }
}