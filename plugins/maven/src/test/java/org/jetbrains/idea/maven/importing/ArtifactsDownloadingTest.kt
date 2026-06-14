/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertUnorderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.downloadArtifacts
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectsTree
import com.intellij.maven.testFramework.fixtures.updateSettingsXmlFully
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.RunAll.Companion.runAll
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThrowableRunnable
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.createDummyArtifact
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenDownloadSourcesRequest
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.server.RemotePathTransformerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import kotlin.io.path.exists

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class ArtifactsDownloadingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion,
    skipPluginResolution = false,
  )
  


  private var defaultDownloadSourcesPolicy: Boolean = true

  @BeforeEach
  fun setUp() {
    val helper = MavenCustomRepositoryHelper(maven.dir, "plugins", "local1")
    helper.copy("plugins", "local1")
    maven.repositoryPath = helper.getTestData("local1")
    defaultDownloadSourcesPolicy = MavenProjectsManager.getInstance(maven.project).importingSettings.isDownloadSourcesAutomatically
    MavenProjectsManager.getInstance(maven.project).importingSettings.isDownloadSourcesAutomatically = false
  }

  @AfterEach
  fun tearDown() {
    runAll(
      ThrowableRunnable<Throwable> {
        MavenProjectsManager.getInstance(maven.project).importingSettings.isDownloadSourcesAutomatically = defaultDownloadSourcesPolicy
      },
      ThrowableRunnable<Throwable> {
      },
    )
  }

  @Test
  fun JavadocsAndSources() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    val sources = maven.repositoryPath.resolve("junit/junit/4.0/junit-4.0-sources.jar")
    val javadoc = maven.repositoryPath.resolve("junit/junit/4.0/junit-4.0-javadoc.jar")

    assertFalse(sources.exists())
    assertFalse(javadoc.exists())

    maven.mavenGeneralSettings.isWorkOffline = false

    maven.downloadArtifacts()

    assertTrue(sources.exists())
    assertTrue(javadoc.exists())
  }

  @Test
  fun IgnoringOfflineSetting() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    val sources = maven.repositoryPath.resolve("junit/junit/4.0/junit-4.0-sources.jar")
    val javadoc = maven.repositoryPath.resolve("junit/junit/4.0/junit-4.0-javadoc.jar")

    assertFalse(sources.exists(), "Sources folder should not exist at test start")
    assertFalse(javadoc.exists(), "Javadoc folder should not exist at test start")

    maven.mavenGeneralSettings.isWorkOffline = true

    val downloadResult = maven.downloadArtifacts()

    val expectedResult = setOf(MavenId("junit", "junit", "4.0"))
    assertEquals(expectedResult, downloadResult.resolvedSources, "Resolved sources")
    assertEquals(expectedResult, downloadResult.resolvedDocs, "Resolved javadocs")
    assertEquals(emptySet<MavenId>(), downloadResult.unresolvedSources, "Unresolved sources")
    assertEquals(emptySet<MavenId>(), downloadResult.unresolvedDocs, "Unresolved javadocs")

    assertTrue(sources.exists(), "Sources folder should exist")
    assertTrue(javadoc.exists(), "Javadoc folder should exist")
  }

  @Test
  fun DownloadingSpecificDependency() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>jmock</groupId>
                        <artifactId>jmock</artifactId>
                        <version>1.2.0</version>
                      </dependency>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    val sources = maven.repositoryPath.resolve("jmock/jmock/1.2.0/jmock-1.2.0-sources.jar")
    val javadoc = maven.repositoryPath.resolve("jmock/jmock/1.2.0/jmock-1.2.0-javadoc.jar")
    assertFalse(sources.exists())
    assertFalse(javadoc.exists())

    val project = maven.projectsTree.rootProjects[0]
    val dep = project.dependencies[0]
    maven.projectsManager.downloadArtifacts(
      MavenDownloadSourcesRequest.builder()
        .forProjects(listOf(project))
        .forArtifacts(listOf(dep))
        .withSources()
        .withDocs()
        .build()
    )

    assertTrue(sources.exists())
    assertTrue(javadoc.exists())
    assertFalse(maven.repositoryPath.resolve("junit/junit/4.0/junit-4.0-sources.jar").exists())
    assertFalse(maven.repositoryPath.resolve("junit/junit/4.0/junit-4.0-javadoc.jar").exists())
  }

  @Test
  fun ReturningNotFoundArtifacts() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>lib</groupId>
                        <artifactId>xxx</artifactId>
                        <version>1</version>
                      </dependency>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    val project = maven.projectsTree.rootProjects[0]
    val unresolvedArtifacts = maven.projectsManager.downloadArtifacts(
      MavenDownloadSourcesRequest.builder()
        .forProjects(listOf(project))
        .forAllArtifacts()
        .withSources()
        .withDocs()
        .build()
    )
    assertUnorderedElementsAreEqual(unresolvedArtifacts.resolvedSources, MavenId("junit", "junit", "4.0"))
    assertUnorderedElementsAreEqual(unresolvedArtifacts.resolvedDocs, MavenId("junit", "junit", "4.0"))
    assertUnorderedElementsAreEqual(unresolvedArtifacts.unresolvedSources, MavenId("lib", "xxx", "1"))
    assertUnorderedElementsAreEqual(unresolvedArtifacts.unresolvedDocs, MavenId("lib", "xxx", "1"))
  }

  @Test
  fun JavadocsAndSourcesForTestDeps() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    val sources = maven.repositoryPath.resolve("junit/junit/4.0/junit-4.0-sources.jar")
    val javadoc = maven.repositoryPath.resolve("junit/junit/4.0/junit-4.0-javadoc.jar")

    assertFalse(sources.exists())
    assertFalse(javadoc.exists())

    maven.downloadArtifacts()

    assertTrue(sources.exists())
    assertTrue(javadoc.exists())
  }

  @Test
  @Throws(Exception::class)
  fun JavadocsAndSourcesForDepsWithClassifiersAndType() = runBlocking {
    val remoteRepo = FileUtilRt.toSystemIndependentName(maven.dir.resolve("repo").toString())
    maven.updateSettingsXmlFully("""<settings>
<mirrors>
  <mirror>
    <id>central</id>
    <url>
${VfsUtilCore.pathToUrl(RemotePathTransformerFactory.createForProject(maven.project).toRemotePath(remoteRepo)!!)}</url>
    <mirrorOf>*</mirrorOf>
  </mirror>
</mirrors>
</settings>
""")

    maven.createDummyArtifact(remoteRepo, "/xxx/xxx/1/xxx-1-sources.jar")
    maven.createDummyArtifact(remoteRepo, "/xxx/xxx/1/xxx-1-javadoc.jar")

    maven.createDummyArtifact(remoteRepo, "/xxx/yyy/1/yyy-1-test-sources.jar")
    maven.createDummyArtifact(remoteRepo, "/xxx/yyy/1/yyy-1-test-javadoc.jar")

    maven.createDummyArtifact(remoteRepo, "/xxx/zzz/1/zzz-1-test-sources.jar")
    maven.createDummyArtifact(remoteRepo, "/xxx/zzz/1/zzz-1-test-javadoc.jar")

    maven.createDummyArtifact(remoteRepo, "/xxx/xxx/1/xxx-1-foo-sources.jar")
    maven.createDummyArtifact(remoteRepo, "/xxx/xxx/1/xxx-1-foo-javadoc.jar")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>xxx</groupId>
                        <artifactId>xxx</artifactId>
                        <version>1</version>
                        <classifier>foo</classifier>
                      </dependency>
                      <dependency>
                        <groupId>xxx</groupId>
                        <artifactId>yyy</artifactId>
                        <version>1</version>
                        <type>test-jar</type>
                      </dependency>
                      <dependency>
                        <groupId>xxx</groupId>
                        <artifactId>zzz</artifactId>
                        <version>1</version>
                        <classifier>foo</classifier>
                        <type>test-jar</type>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    val files1 = listOf(maven.repositoryPath.resolve("xxx/xxx/1/xxx-1-sources.jar"),
                        maven.repositoryPath.resolve("xxx/xxx/1/xxx-1-javadoc.jar"),
                        maven.repositoryPath.resolve("xxx/yyy/1/yyy-1-test-sources.jar"),
                        maven.repositoryPath.resolve("xxx/yyy/1/yyy-1-test-javadoc.jar"))

    val files2 = listOf(maven.repositoryPath.resolve("xxx/xxx/1/xxx-1-foo-sources.jar"),
                        maven.repositoryPath.resolve("xxx/xxx/1/xxx-1-foo-javadoc.jar"),
                        maven.repositoryPath.resolve("xxx/zzz/1/zzz-1-test-foo-sources.jar"),
                        maven.repositoryPath.resolve("xxx/zzz/1/zzz-1-test-foo-javadoc.jar"))

    for (each in files1) {
      assertFalse(each.exists(), each.toString())
    }
    for (each in files2) {
      assertFalse(each.exists(), each.toString())
    }
    maven.downloadArtifacts()

    for (each in files1) {
      assertTrue(each.exists(), each.toString())
    }
    for (each in files2) {
      assertFalse(each.exists(), each.toString())
    }
  }

  @Test
  fun DownloadingPlugins() = runBlocking {
    try {
      maven.importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <version>1</version>
                      <build>
                        <plugins>
                          <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-surefire-plugin</artifactId>
                            <version>2.4.2</version>
                          </plugin>
                        </plugins>
                      </build>
                      """.trimIndent())

      val f = maven.repositoryPath.resolve("org/apache/maven/plugins/maven-surefire-plugin/2.4.2/maven-surefire-plugin-2.4.2.jar")

      assertTrue(f.exists())
    }
    finally {
      // do not lock files by maven process
      MavenServerManager.getInstance().closeAllConnectorsAndWait()
    }
  }

  @Test
  fun DownloadBuildExtensionsOnResolve() = runBlocking {
    val f = maven.repositoryPath.resolve("org/apache/maven/wagon/wagon-ftp/2.10/wagon-ftp-2.10.pom")
    assertFalse(f.exists())

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <extensions>
                        <extension>
                          <groupId>org.apache.maven.wagon</groupId>
                          <artifactId>wagon-ftp</artifactId>
                          <version>2.10</version>
                        </extension>
                      </extensions>
                    </build>
                    """.trimIndent())

    assertTrue(f.exists())
  }
}
