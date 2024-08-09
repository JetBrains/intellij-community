/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.dom.MavenDomWithIndicesTestCase
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog
import org.jetbrains.idea.maven.model.MavenId
import org.junit.Test

class AddMavenDependencyQuickFixTest : MavenDomWithIndicesTestCase() {
  
  private fun findAddMavenIntention(): IntentionAction {
    for (intention in fixture.getAvailableIntentions()) {
      if (intention.getText().contains("Add Maven")) {
        return intention
      }
    }

    throw RuntimeException("Add Maven intention not found")
  }

  @Test
  fun testAddDependency() = runBlocking {
    val f = createProjectSubFile("src/main/java/A.java", """
      import org.apache.commons.io.IOUtils;

      public class Aaa {

        public void xxx() {
          IOUtil<caret>s u;
        }

      }
      """.trimIndent())

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    refreshFiles(listOf(f))
    fixture.configureFromExistingVirtualFile(f)

    val intentionAction = findAddMavenIntention()

    MavenArtifactSearchDialog.ourResultForTest = listOf(MavenId("commons-io", "commons-io", "2.4"))

    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          intentionAction.invoke(project, fixture.getEditor(), fixture.getFile())
        }
      }
    }
    val pomText = readAction { PsiManager.getInstance(project).findFile(projectPom)!!.getText() }
    assertTrue(pomText.matches(
      "(?s).*<dependency>\\s*<groupId>commons-io</groupId>\\s*<artifactId>commons-io</artifactId>\\s*<version>2.4</version>\\s*</dependency>.*".toRegex()))

  }

  @Test
  fun testAddDependencyTwice() = runBlocking {
    val f = createProjectSubFile("src/main/java/A.java", """
      import org.apache.commons.io.IOUtils;

      public class Aaa {

        public void xxx() {
          IOUtil<caret>s u;
        }

      }
      """.trimIndent())

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    refreshFiles(listOf(f))
    fixture.configureFromExistingVirtualFile(f)

    val intentionAction = findAddMavenIntention()

    MavenArtifactSearchDialog.ourResultForTest = listOf(MavenId("commons-io", "commons-io", "2.4"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          intentionAction.invoke(project, fixture.getEditor(), fixture.getFile())
        }
      }
    }
    MavenArtifactSearchDialog.ourResultForTest = listOf(MavenId("commons-io", "commons-io", "2.4"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          intentionAction.invoke(project, fixture.getEditor(), fixture.getFile())
        }
      }
    }
    val pomText = readAction { PsiManager.getInstance(project).findFile(projectPom)!!.getText() }
    assertEquals("""
                    <?xml version="1.0"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                      <modelVersion>4.0.0</modelVersion>
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                        <dependencies>
                            <dependency>
                                <groupId>commons-io</groupId>
                                <artifactId>commons-io</artifactId>
                                <version>2.4</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """.trimIndent(), pomText)
  }

  @Test
  fun testChangeDependencyScopeIfWasInTest() = runBlocking {
    val f = createProjectSubFile("src/main/java/A.java", """
      import org.apache.commons.io.IOUtils;

      public class Aaa {

        public void xxx() {
          IOUtil<caret>s u;
        }

      }
      """.trimIndent())

    importProjectAsync("""
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>commons-io</groupId>
                        <artifactId>commons-io</artifactId>
                        <version>2.4</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    refreshFiles(listOf(f))
    fixture.configureFromExistingVirtualFile(f)

    val intentionAction = findAddMavenIntention()

    MavenArtifactSearchDialog.ourResultForTest = listOf(MavenId("commons-io", "commons-io", "2.4"))
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          intentionAction.invoke(project, fixture.getEditor(), fixture.getFile())
        }
      }
    }
    val pomText = readAction { PsiManager.getInstance(project).findFile(projectPom)!!.getText() }
    assertEquals("""
                   <?xml version="1.0"?>
                   <project xmlns="http://maven.apache.org/POM/4.0.0"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                     <modelVersion>4.0.0</modelVersion>
                   <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                   <dependencies>
                     <dependency>
                       <groupId>commons-io</groupId>
                       <artifactId>commons-io</artifactId>
                       <version>2.4</version>
                     </dependency>
                   </dependencies></project>
                   """.trimIndent(), pomText)
  }

  @Test
  fun testAddDependencyInTest() = runBlocking {
    val f = createProjectSubFile("src/test/java/A.java", """
      import org.apache.commons.io.IOUtils;

      public class Aaa {

        public void xxx() {
          IOUtil<caret>s u;
        }

      }
      """.trimIndent())

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    refreshFiles(listOf(f))
    fixture.configureFromExistingVirtualFile(f)

    val intentionAction = findAddMavenIntention()

    MavenArtifactSearchDialog.ourResultForTest = listOf(MavenId("commons-io", "commons-io", "2.4"))

    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          intentionAction.invoke(project, fixture.getEditor(), fixture.getFile())
        }
      }
    }
    val pomText = readAction { PsiManager.getInstance(project).findFile(projectPom)!!.getText() }
    assertTrue(pomText.matches(
      "(?s).*<dependency>\\s*<groupId>commons-io</groupId>\\s*<artifactId>commons-io</artifactId>\\s*<version>2.4</version>\\s*<scope>test</scope>\\s*</dependency>.*".toRegex()))
  }
}
