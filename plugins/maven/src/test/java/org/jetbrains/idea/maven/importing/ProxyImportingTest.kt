// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.maven.testFramework.utils.MavenHttpProxyServerFixture
import com.intellij.maven.testFramework.utils.MavenHttpRepositoryServerFixture
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.junit.Test
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

class ProxyImportingTest : MavenMultiVersionImportingTestCase() {
  lateinit var myRepositoryFixture: MavenHttpRepositoryServerFixture;
  lateinit var myProxyFixture: MavenHttpProxyServerFixture;
  lateinit var myHelper: MavenCustomRepositoryHelper;

  override fun setUp() {
    super.setUp()
    myRepositoryFixture = MavenHttpRepositoryServerFixture()
    myRepositoryFixture.setUp()
    val port = URI(myRepositoryFixture.url()).port
    val proxyPort = NetUtils.findAvailableSocketPort()
    myProxyFixture = MavenHttpProxyServerFixture(mapOf("mavendummycentral.jetbrains.com" to port), proxyPort, AppExecutorUtil.getAppExecutorService())
    myProxyFixture.setUp()
  }

  private fun setupRepository(local: String, remote: String) {
    myHelper = MavenCustomRepositoryHelper(dir, local, remote)
    val remoteRepoPath = myHelper.getTestData(remote)
    myRepositoryFixture.startRepositoryFor(remoteRepoPath.toString())
    val localRepoPath = myHelper.getTestData(local)
    repositoryPath = localRepoPath
  }

  private fun setupSettingsXml(localRepoPath: Path, withProxyAuth: Boolean) {
    val proxyAuthInfo = if (withProxyAuth) {
      """
        <username>$proxyUsername</username>
        <password>$proxyPassword</password>
      """
    }
    else ""
    val settingsXml = createProjectSubFile(
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
              <repositories>
                <repository>
                  <id>central</id>
                  <name>my-mirror</name>
                  <url>http://mavendummycentral.jetbrains.com/</url>
                  <blocked>false</blocked>
                </repository>
              </repositories>
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
            </settings>
            """.trimIndent())
    mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable {
        myProxyFixture.tearDown()
      },
      ThrowableRunnable {
        myRepositoryFixture.tearDown()
      },
      ThrowableRunnable { super.tearDown() }).run()
  }

  @Test
  fun testDownloadDependencyUsingProxy() = runBlocking {
    setupRepository("local1", "remote")
    setupSettingsXml(repositoryPath.toAbsolutePath(), false)
    removeFromLocalRepository("org/mytest/myartifact/")
    importProjectAsync("""
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
    assertTrue("File should be downloaded", repositoryPath.resolve("org/mytest/myartifact/1.0/myartifact-1.0.jar").isRegularFile())
  }


  @Test
  fun testDownloadDependencyUsingProxyWithAuthorization() = runBlocking {
    setupRepository("local1", "remote")
    removeFromLocalRepository("org/mytest/myartifact/")
    setupSettingsXml(repositoryPath.toAbsolutePath(), true)
    myProxyFixture.requireAuthentication(proxyUsername, proxyPassword)
    assertFalse("File should be deleted", myHelper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isRegularFile())
    importProjectAsync("""
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
    assertTrue("File should be downloaded", myHelper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isRegularFile())
  }

  @Test
  fun testResolveJavadocsAndSourcesUsingProxy() = runBlocking {
    setupRepository("local1", "remote")
    removeFromLocalRepository("org/mytest/")
    assertFalse("File should be deleted", myHelper.getTestData("local1/org/mytest/").exists())
    setupSettingsXml(repositoryPath.toAbsolutePath(), true)
    myProxyFixture.requireAuthentication(proxyUsername, proxyPassword)
    importProjectAsync("""
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

    projectsManager.downloadArtifacts(projectsManager.rootProjects, null, true, true)

    assertContainsElements(myProxyFixture.requestedFiles, "/org/mytest/myartifact/1.0/myartifact-1.0.jar", "/org/mytest/myartifact/1.0/myartifact-1.0.pom")
    assertContainsElements(myProxyFixture.requestedFiles, "/org/mytest/myartifact/1.0/myartifact-1.0-javadoc.jar", "/org/mytest/myartifact/1.0/myartifact-1.0-sources.jar")
    assertTrue("Source jar should be downloaded", myHelper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0-sources.jar").isRegularFile())
    assertTrue("Javadoc jar should be downloaded", myHelper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0-javadoc.jar").isRegularFile())
  }

  @Test
  fun testResolvePluginsUsingProxy() = runBlocking {
    setupRepository("local1", "plugins")
    removeFromLocalRepository("intellij/test/")
    setupSettingsXml(repositoryPath.toAbsolutePath(), true)
    myProxyFixture.requireAuthentication(proxyUsername, proxyPassword)
    assertFalse("File should be deleted", myHelper.getTestData("local1/intellij/test/maven-extension/1.0/maven-extension-1.0.jar").isRegularFile())
    importProjectAsync("""
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
    assertContainsElements(myProxyFixture.requestedFiles, "/intellij/test/maven-extension/1.0/maven-extension-1.0.jar", "/intellij/test/maven-extension/1.0/maven-extension-1.0.pom")
    assertTrue("File should be downloaded", myHelper.getTestData("local1/intellij/test/maven-extension/1.0/maven-extension-1.0.jar").isRegularFile())

  }

  companion object {
    const val proxyUsername = "IntellijIdea"
    const val proxyPassword = "Rulezzz!!!111"
  }
}