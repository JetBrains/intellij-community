// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.utils.MavenHttpProxyServerFixture
import com.intellij.maven.testFramework.utils.MavenHttpRepositoryServerFixture
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.removeFromLocalRepository
import org.jetbrains.idea.maven.project.MavenDownloadSourcesRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class ProxyImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion,
    skipPluginResolution = false,
  )
  

  lateinit var myRepositoryFixture: MavenHttpRepositoryServerFixture
  lateinit var myProxyFixture: MavenHttpProxyServerFixture
  lateinit var myHelper: MavenCustomRepositoryHelper

  @BeforeEach
  fun setUp() {
    myRepositoryFixture = MavenHttpRepositoryServerFixture()
    myRepositoryFixture.setUp()
    val port = URI(myRepositoryFixture.url()).port
    myProxyFixture = MavenHttpProxyServerFixture(mapOf("mavendummycentral.jetbrains.com" to port), AppExecutorUtil.getAppExecutorService())
    myProxyFixture.setUp()
  }

  private fun setupRepository(local: String, remote: String) {
    myHelper = MavenCustomRepositoryHelper(maven.dir, local, remote)
    val remoteRepoPath = myHelper.getTestData(remote)
    myRepositoryFixture.startRepositoryFor(remoteRepoPath.toString())
    val localRepoPath = myHelper.getTestData(local)
    maven.repositoryPath = localRepoPath
  }

  private fun setupSettingsXml(localRepoPath: Path, withProxyAuth: Boolean) {
    val proxyAuthInfo = if (withProxyAuth) {
      """
        <username>$proxyUsername</username>
        <password>$proxyPassword</password>
      """
    }
    else ""
    val settingsXml = maven.createProjectSubFile(
      "settings.xml",
      """
            <settings>
            
              <localRepository>$localRepoPath</localRepository>
              
              <mirrors>
                <mirror>
                  <id>central-mirror</id>
                  <name>my-mirror</name>
                  <url>http://mavendummycentral.jetbrains.com/</url>
                  <mirrorOf>central</mirrorOf>
                  <blocked>false</blocked>
                </mirror>
                <mirror>
                  <id>maven-default-http-blocker</id>
                  <name>maven-default-http-blocker-settings</name>
                  <url>http://mavendummycentral.jetbrains.com/</url>
                  <mirrorOf>*</mirrorOf>
                  <blocked>false</blocked>
                </mirror>
              </mirrors>
              
              <proxies>
                <proxy>
                  <id>my</id>
                  <active>true</active>
                  <protocol>http</protocol>
                  <host>127.0.0.1</host>
                  $proxyAuthInfo
                  <port>${myProxyFixture.port}</port>
                </proxy>
              </proxies>
              
              <profiles>
                <profile>
                  <id>custom-repos</id>
                  <repositories>
                    <repository>
                      <id>central</id>
                      <name>my-mirror</name>
                      <url>http://mavendummycentral.jetbrains.com/</url>
                      <blocked>false</blocked>
                    </repository>
                  </repositories>
                  <pluginRepositories>
                    <pluginRepository>
                      <id>central-plugins</id>
                      <url>http://mavendummycentral.jetbrains.com/</url>
                      <blocked>false</blocked>
                    </pluginRepository>
                  </pluginRepositories>        
                </profile>
              </profiles>
            
              <activeProfiles>
                <activeProfile>custom-repos</activeProfile>
              </activeProfiles>
              
            </settings>
            """.trimIndent())
    maven.mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)
  }

  @AfterEach
  fun tearDown() {
    RunAll(
      ThrowableRunnable {
        myProxyFixture.tearDown()
      },
      ThrowableRunnable {
        myRepositoryFixture.tearDown()
      }).run()
  }

  @Test
  fun testDownloadDependencyUsingProxy() = runBlocking {
    setupRepository("local1", "remote")
    setupSettingsXml(maven.repositoryPath.toAbsolutePath(), false)
    maven.removeFromLocalRepository("org/mytest/myartifact/")
    maven.importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>org.mytest</groupId>
                           <artifactId>myartifact</artifactId>
                           <version>1.0</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent()
    )
    assertContainsElements(myProxyFixture.requestedFiles, "/org/mytest/myartifact/1.0/myartifact-1.0.jar", "/org/mytest/myartifact/1.0/myartifact-1.0.pom")
    assertTrue(maven.repositoryPath.resolve("org/mytest/myartifact/1.0/myartifact-1.0.jar").isRegularFile(), "File should be downloaded")
  }


  @Test
  fun testDownloadDependencyUsingProxyWithAuthorization() = runBlocking {
    setupRepository("local1", "remote")
    maven.removeFromLocalRepository("org/mytest/myartifact/")
    setupSettingsXml(maven.repositoryPath.toAbsolutePath(), true)
    myProxyFixture.requireAuthentication(proxyUsername, proxyPassword)
    assertFalse(myHelper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isRegularFile(), "File should be deleted")
    maven.importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>org.mytest</groupId>
                           <artifactId>myartifact</artifactId>
                           <version>1.0</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent()
    )
    assertContainsElements(myProxyFixture.requestedFiles, "/org/mytest/myartifact/1.0/myartifact-1.0.jar", "/org/mytest/myartifact/1.0/myartifact-1.0.pom")
    assertTrue(myHelper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isRegularFile(), "File should be downloaded")
  }

  @Test
  fun testResolveJavadocsAndSourcesUsingProxy() = runBlocking {
    setupRepository("local1", "remote")
    maven.removeFromLocalRepository("org/mytest/")
    assertFalse(myHelper.getTestData("local1/org/mytest/").exists(), "File should be deleted")
    setupSettingsXml(maven.repositoryPath.toAbsolutePath(), true)
    myProxyFixture.requireAuthentication(proxyUsername, proxyPassword)
    maven.importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>org.mytest</groupId>
                           <artifactId>myartifact</artifactId>
                           <version>1.0</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.projectsManager.downloadArtifacts(
      MavenDownloadSourcesRequest.builder()
        .forProjects(maven.projectsManager.rootProjects)
        .forAllArtifacts()
        .withSources()
        .withDocs()
        .build()
    )

    assertContainsElements(myProxyFixture.requestedFiles, "/org/mytest/myartifact/1.0/myartifact-1.0.jar", "/org/mytest/myartifact/1.0/myartifact-1.0.pom")
    assertContainsElements(myProxyFixture.requestedFiles, "/org/mytest/myartifact/1.0/myartifact-1.0-javadoc.jar", "/org/mytest/myartifact/1.0/myartifact-1.0-sources.jar")
    assertTrue(myHelper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0-sources.jar").isRegularFile(), "Source jar should be downloaded")
    assertTrue(myHelper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0-javadoc.jar").isRegularFile(), "Javadoc jar should be downloaded")
  }

  @Test
  fun testResolvePluginsUsingProxy() = runBlocking {
    setupRepository("local1", "plugins")
    maven.removeFromLocalRepository("intellij/test/")
    setupSettingsXml(maven.repositoryPath.toAbsolutePath(), true)
    myProxyFixture.requireAuthentication(proxyUsername, proxyPassword)
    assertFalse(myHelper.getTestData("local1/intellij/test/maven-extension/1.0/maven-extension-1.0.jar").isRegularFile(), "Jar file should be deleted")
    assertFalse(myHelper.getTestData("local1/intellij/test/maven-extension/1.0/maven-extension-1.0.pom").isRegularFile(), "Pom file should be deleted")
    assertTrue(myHelper.getTestData("plugins/intellij/test/maven-extension/1.0/maven-extension-1.0.jar").isRegularFile(), "Jar file not found in plugin repo")
    assertTrue(myHelper.getTestData("plugins/intellij/test/maven-extension/1.0/maven-extension-1.0.pom").isRegularFile(), "Pom file not found in plugin repo")
    maven.importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                           <plugins>
                               <plugin>
                                   <groupId>intellij.test</groupId>
                                   <artifactId>maven-extension</artifactId>
                                   <version>1.0</version>
                               </plugin>
                           </plugins>
                       </build>
                       """.trimIndent())
    assertTrue(myHelper.getTestData("local1/intellij/test/maven-extension/1.0/maven-extension-1.0.pom").isRegularFile(), "Pom file should be downloaded")
    assertTrue(myHelper.getTestData("local1/intellij/test/maven-extension/1.0/maven-extension-1.0.jar").isRegularFile(), "Jar file should be downloaded")
    assertContainsElements(myProxyFixture.requestedFiles, "/intellij/test/maven-extension/1.0/maven-extension-1.0.jar", "/intellij/test/maven-extension/1.0/maven-extension-1.0.pom")
  }

  companion object {
    const val proxyUsername = "IntellijIdea"
    const val proxyPassword = "Rulezzz!!!111"
  }
}