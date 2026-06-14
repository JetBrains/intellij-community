// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.fixtures.waitForImportWithinTimeout
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog
import org.jetbrains.idea.maven.model.MavenId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class AddMavenDependencyQuickFixTest(mavenVersion: String, private val modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    indices = MavenDomTestFixtureIndices("local1", listOf("local2")),
  )

  private fun findAddMavenIntention(): IntentionAction {
    for (intention in maven.fixture.getAvailableIntentions()) {
      if (intention.getText().contains("Add Maven")) {
        return intention
      }
    }

    throw RuntimeException("Add Maven intention not found")
  }

  @Test
  fun testAddDependency() = runBlocking {
    val f = maven.createProjectSubFile("src/main/java/A.java", """
      import org.apache.commons.io.IOUtils;

      public class Aaa {

        public void xxx() {
          IOUtil<caret>s u;
        }

      }
      """.trimIndent())

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    maven.refreshFiles(listOf(f))
    maven.fixture.configureFromExistingVirtualFile(f)

    val intentionAction = findAddMavenIntention()

    MavenArtifactSearchDialog.ourResultForTest = listOf(MavenId("commons-io", "commons-io", "2.4"))

    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          intentionAction.invoke(maven.project, maven.fixture.getEditor(), maven.fixture.getFile())
        }
      }
    }
    val pomText = readAction { PsiManager.getInstance(maven.project).findFile(maven.projectPom)!!.getText() }
    assertTrue(pomText.matches(
      "(?s).*<dependency>\\s*<groupId>commons-io</groupId>\\s*<artifactId>commons-io</artifactId>\\s*<version>2.4</version>\\s*</dependency>.*".toRegex()))

  }

  @Test
  fun testAddDependencyTwice() = runBlocking {
    val f = maven.createProjectSubFile("src/main/java/A.java", """
      import org.apache.commons.io.IOUtils;

      public class Aaa {

        public void xxx() {
          IOUtil<caret>s u;
        }

      }
      """.trimIndent())

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    maven.refreshFiles(listOf(f))
    maven.fixture.configureFromExistingVirtualFile(f)

    val intentionAction = findAddMavenIntention()

    MavenArtifactSearchDialog.ourResultForTest = listOf(MavenId("commons-io", "commons-io", "2.4"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          intentionAction.invoke(maven.project, maven.fixture.getEditor(), maven.fixture.getFile())
        }
      }
    }
    IndexingTestUtil.waitUntilIndexesAreReady(maven.project)
    MavenArtifactSearchDialog.ourResultForTest = listOf(MavenId("commons-io", "commons-io", "2.4"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          intentionAction.invoke(maven.project, maven.fixture.getEditor(), maven.fixture.getFile())
        }
      }
    }
    val pomText = readAction { PsiManager.getInstance(maven.project).findFile(maven.projectPom)!!.getText() }
    assertEquals("""
                    <?xml version="1.0"?>
                    <project xmlns="http://maven.apache.org/POM/$modelVersion"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/POM/$modelVersion http://maven.apache.org/xsd/maven-$modelVersion.xsd">
                      <modelVersion>$modelVersion</modelVersion>
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
    val f = maven.createProjectSubFile("src/main/java/A.java", """
      import org.apache.commons.io.IOUtils;

      public class Aaa {

        public void xxx() {
          IOUtil<caret>s u;
        }

      }
      """.trimIndent())

    maven.importProjectAsync("""
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

    maven.refreshFiles(listOf(f))
    maven.fixture.configureFromExistingVirtualFile(f)

    val intentionAction = findAddMavenIntention()

    MavenArtifactSearchDialog.ourResultForTest = listOf(MavenId("commons-io", "commons-io", "2.4"))
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          intentionAction.invoke(maven.project, maven.fixture.getEditor(), maven.fixture.getFile())
        }
      }
    }
    val pomText = readAction { PsiManager.getInstance(maven.project).findFile(maven.projectPom)!!.getText() }
    assertEquals("""
                   <?xml version="1.0"?>
                   <project xmlns="http://maven.apache.org/POM/$modelVersion"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://maven.apache.org/POM/$modelVersion http://maven.apache.org/xsd/maven-$modelVersion.xsd">
                     <modelVersion>$modelVersion</modelVersion>
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
    val f = maven.createProjectSubFile("src/test/java/A.java", """
      import org.apache.commons.io.IOUtils;

      public class Aaa {

        public void xxx() {
          IOUtil<caret>s u;
        }

      }
      """.trimIndent())

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    maven.refreshFiles(listOf(f))
    maven.fixture.configureFromExistingVirtualFile(f)

    val intentionAction = findAddMavenIntention()

    MavenArtifactSearchDialog.ourResultForTest = listOf(MavenId("commons-io", "commons-io", "2.4"))

    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          intentionAction.invoke(maven.project, maven.fixture.getEditor(), maven.fixture.getFile())
        }
      }
    }
    val pomText = readAction { PsiManager.getInstance(maven.project).findFile(maven.projectPom)!!.getText() }
    assertTrue(pomText.matches(
      "(?s).*<dependency>\\s*<groupId>commons-io</groupId>\\s*<artifactId>commons-io</artifactId>\\s*<version>2.4</version>\\s*<scope>test</scope>\\s*</dependency>.*".toRegex()))
  }
}
