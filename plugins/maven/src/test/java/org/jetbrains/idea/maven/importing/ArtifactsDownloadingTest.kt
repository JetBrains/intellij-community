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

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.Test
import java.io.File
import java.util.*

class ArtifactsDownloadingTest : ArtifactsDownloadingTestCase() {
    
  @Test
  fun JavadocsAndSources() = runBlocking {
    importProjectAsync("""
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

    val sources = File(repositoryPath, "/junit/junit/4.0/junit-4.0-sources.jar")
    val javadoc = File(repositoryPath, "/junit/junit/4.0/junit-4.0-javadoc.jar")

    assertFalse(sources.exists())
    assertFalse(javadoc.exists())

    downloadArtifacts()

    assertTrue(sources.exists())
    assertTrue(javadoc.exists())
  }

  @Test
  fun IgnoringOfflineSetting() = runBlocking {
    importProjectAsync("""
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

    val sources = File(repositoryPath, "/junit/junit/4.0/junit-4.0-sources.jar")
    val javadoc = File(repositoryPath, "/junit/junit/4.0/junit-4.0-javadoc.jar")

    assertFalse(sources.exists())
    assertFalse(javadoc.exists())

    mavenGeneralSettings.isWorkOffline = false
    projectsManager.embeddersManager.reset() // to recognize change
    downloadArtifacts()

    assertTrue(sources.exists())
    assertTrue(javadoc.exists())

    FileUtil.delete(sources)
    FileUtil.delete(javadoc)

    mavenGeneralSettings.isWorkOffline = true
    projectsManager.embeddersManager.reset() // to recognize change

    downloadArtifacts()

    assertTrue(sources.exists())
    assertTrue(javadoc.exists())
  }

  @Test
  fun DownloadingSpecificDependency() = runBlocking {
    importProjectAsync("""
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

    val sources = File(repositoryPath, "/jmock/jmock/1.2.0/jmock-1.2.0-sources.jar")
    val javadoc = File(repositoryPath, "/jmock/jmock/1.2.0/jmock-1.2.0-javadoc.jar")
    assertFalse(sources.exists())
    assertFalse(javadoc.exists())

    val project = projectsTree.rootProjects[0]
    val dep = project.dependencies[0]
    projectsManager.downloadArtifacts(listOf(project), listOf(dep), true, true)

    assertTrue(sources.exists())
    assertTrue(javadoc.exists())
    assertFalse(File(repositoryPath, "/junit/junit/4.0/junit-4.0-sources.jar").exists())
    assertFalse(File(repositoryPath, "/junit/junit/4.0/junit-4.0-javadoc.jar").exists())
  }

  @Test
  fun ReturningNotFoundArtifacts() = runBlocking {
    needFixForMaven4()
    importProjectAsync("""
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

    val project = projectsTree.rootProjects[0]
    val unresolvedArtifacts = projectsManager.downloadArtifacts(listOf(project), null, true, true)
    assertUnorderedElementsAreEqual(unresolvedArtifacts.resolvedSources, MavenId("junit", "junit", "4.0"))
    assertUnorderedElementsAreEqual(unresolvedArtifacts.resolvedDocs, MavenId("junit", "junit", "4.0"))
    assertUnorderedElementsAreEqual(unresolvedArtifacts.unresolvedSources, MavenId("lib", "xxx", "1"))
    assertUnorderedElementsAreEqual(unresolvedArtifacts.unresolvedDocs, MavenId("lib", "xxx", "1"))
  }

  @Test
  fun JavadocsAndSourcesForTestDeps() = runBlocking {
    importProjectAsync("""
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

    val sources = File(repositoryPath, "/junit/junit/4.0/junit-4.0-sources.jar")
    val javadoc = File(repositoryPath, "/junit/junit/4.0/junit-4.0-javadoc.jar")

    assertFalse(sources.exists())
    assertFalse(javadoc.exists())

    downloadArtifacts()

    assertTrue(sources.exists())
    assertTrue(javadoc.exists())
  }

  @Test
  @Throws(Exception::class)
  fun JavadocsAndSourcesForDepsWithClassifiersAndType() = runBlocking {
    needFixForMaven4()
    val remoteRepo = FileUtil.toSystemIndependentName(dir.path + "/repo")
    updateSettingsXmlFully("""<settings>
<mirrors>
  <mirror>
    <id>central</id>
    <url>
${VfsUtilCore.pathToUrl(pathTransformer.toRemotePath(remoteRepo)!!)}</url>
    <mirrorOf>*</mirrorOf>
  </mirror>
</mirrors>
</settings>
""")

    createDummyArtifact(remoteRepo, "/xxx/xxx/1/xxx-1-sources.jar")
    createDummyArtifact(remoteRepo, "/xxx/xxx/1/xxx-1-javadoc.jar")

    createDummyArtifact(remoteRepo, "/xxx/yyy/1/yyy-1-test-sources.jar")
    createDummyArtifact(remoteRepo, "/xxx/yyy/1/yyy-1-test-javadoc.jar")

    createDummyArtifact(remoteRepo, "/xxx/xxx/1/xxx-1-foo-sources.jar")
    createDummyArtifact(remoteRepo, "/xxx/xxx/1/xxx-1-foo-javadoc.jar")

    createDummyArtifact(remoteRepo, "/xxx/zzz/1/zzz-1-test-foo-sources.jar")
    createDummyArtifact(remoteRepo, "/xxx/zzz/1/zzz-1-test-foo-javadoc.jar")

    importProjectAsync("""
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

  val files1 = listOf(File(repositoryPath, "/xxx/xxx/1/xxx-1-sources.jar"),
                      File(repositoryPath, "/xxx/xxx/1/xxx-1-javadoc.jar"),
                      File(repositoryPath, "/xxx/yyy/1/yyy-1-test-sources.jar"),
                      File(repositoryPath, "/xxx/yyy/1/yyy-1-test-javadoc.jar"))

    val files2 = listOf(File(repositoryPath, "/xxx/xxx/1/xxx-1-foo-sources.jar"),
                        File(repositoryPath, "/xxx/xxx/1/xxx-1-foo-javadoc.jar"),
                        File(repositoryPath, "/xxx/zzz/1/zzz-1-test-foo-sources.jar"),
                        File(repositoryPath, "/xxx/zzz/1/zzz-1-test-foo-javadoc.jar"))

    for (each in files1) {
      assertFalse(each.toString(), each.exists())
    }
    for (each in files2) {
      assertFalse(each.toString(), each.exists())
    }
    downloadArtifacts()

    for (each in files1) {
      assertTrue(each.toString(), each.exists())
    }
    for (each in files2) {
      assertFalse(each.toString(), each.exists())
    }
  }

  @Test
  fun DownloadingPlugins() = runBlocking {
    try {
      importProjectAsync("""
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

      val f = File(repositoryPath, "/org/apache/maven/plugins/maven-surefire-plugin/2.4.2/maven-surefire-plugin-2.4.2.jar")

      assertTrue(f.exists())
    }
    finally {
      // do not lock files by maven process
      MavenServerManager.getInstance().closeAllConnectorsAndWait()
    }
  }

  @Test
  fun DownloadBuildExtensionsOnResolve() = runBlocking {
    val f = File(repositoryPath, "/org/apache/maven/wagon/wagon-ftp/2.10/wagon-ftp-2.10.pom")
    assertFalse(f.exists())

    importProjectAsync("""
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
