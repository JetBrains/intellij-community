// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.search

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import com.intellij.testFramework.UsefulTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test

class MavenSearchTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun `test searching POM files by module name`() = runBlocking {
    createProjectPom("""<groupId>test</groupId>
                     <artifactId>p1</artifactId>
                     <packaging>pom</packaging>
                     <version>1</version>
                     <modules>
                       <module>module1</module>
                       <module>module2</module>
                     </modules>""")
    val m1File = createModulePom("module1",
                    """<parent>
                      <groupId>test</groupId>
                      <artifactId>p1</artifactId>
                      <version>1</version>
                    </parent>
                    <groupId>test</groupId>
                    <artifactId>module1</artifactId>
                    <version>1</version>""")
    val m2File = createModulePom("module2",
                    """<parent>
                      <groupId>test</groupId>
                      <artifactId>p1</artifactId>
                      <version>1</version>
                    </parent>
                    <groupId>test</groupId>
                    <artifactId>module2</artifactId>
                    <version>1</version>""")
    importProjectAsync()

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val m1Psi = PsiManager.getInstance(project).findFile(m1File)
        val m2Psi = PsiManager.getInstance(project).findFile(m2File)
        UsefulTestCase.assertContainsElements(lookForFiles("module1"), m1Psi)
        UsefulTestCase.assertContainsElements(lookForFiles("module2"), m2Psi)
      }
    }
  }

  private fun lookForFiles(pattern: String): List<Any> =
    createFileContributor()
      .apply { Disposer.register(testRootDisposable, this) }
      .search(pattern, MockProgressIndicator())

  private fun createFileContributor(): SearchEverywhereContributor<Any> =
    FileSearchEverywhereContributor(createStubEvent())

  private fun createStubEvent(): AnActionEvent {
    val dataContext = SimpleDataContext.getProjectContext(project)
    return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext)
  }
}