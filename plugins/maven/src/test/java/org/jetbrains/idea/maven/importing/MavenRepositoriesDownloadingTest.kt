// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.RunAll.Companion.runAll
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.AppExecutorUtil
import com.sun.net.httpserver.Authenticator
import com.sun.net.httpserver.BasicAuthenticator
import com.sun.net.httpserver.HttpServer
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.model.MavenProjectProblem
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.server.MisconfiguredPlexusDummyEmbedder
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class MavenRepositoriesDownloadingTest : MavenMultiVersionImportingTestCase() {

  @ReviseWhenPortedToJDK("18") //replace with SimpleFileServers

  private lateinit var myServer: HttpServer
  private lateinit var myUrl: String

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    myServer = HttpServer.create()
    myServer.setExecutor(AppExecutorUtil.getAppExecutorService())
    myServer.bind(InetSocketAddress(LOCALHOST, 0), 5)
    myServer.start()
    myUrl = "http://" + LOCALHOST + ":" + myServer.getAddress().port
  }


  public override fun tearDown() {
    runAll(
      ThrowableRunnable<Throwable> {
        myServer?.stop(0)
      },
      ThrowableRunnable<Throwable> { super.tearDown() }
    )
  }

  @Test
  @Throws(Exception::class)
  fun testDownloadedFromRepository() {
    val helper = MavenCustomRepositoryHelper(myDir, "local1", "remote")
    val remoteRepoPath = helper.getTestDataPath("remote")
    val localRepoPath = helper.getTestDataPath("local1")
    setupRemoteRepositoryServer(remoteRepoPath)
    repositoryPath = localRepoPath
    val settingsXml = createProjectSubFile(
      "settings.xml",
      """
       <settings>
          <localRepository>$localRepoPath</localRepository>
       </settings>
       """.trimIndent())
    mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)
    removeFromLocalRepository("org/mytest/myartifact/")
    assertFalse(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isFile)
    importProject(pom())
    assertTrue(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isFile)
  }

  @Test
  @Throws(Exception::class)
  fun testDownloadedFromRepositoryWithAuthentification() {
    val helper = MavenCustomRepositoryHelper(myDir, "local1", "remote")
    val remoteRepoPath = helper.getTestDataPath("remote")
    val localRepoPath = helper.getTestDataPath("local1")
    setupRemoteRepositoryServer(remoteRepoPath, object : BasicAuthenticator("/") {
      override fun checkCredentials(username: String?, password: String?): Boolean {
        return username == USERNAME && password == PASSWORD
      }
    })
    repositoryPath = localRepoPath
    val settingsXml = createProjectSubFile(
      "settings.xml",
      """
       <settings>
          <localRepository>$localRepoPath</localRepository>
          <servers>
            <server>
              <id>my-http-repository</id>
              <username>${USERNAME}</username>
              <password>${PASSWORD}</password>
            </server>
          </servers>
          
       </settings>
       """.trimIndent())

    mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)
    removeFromLocalRepository("org/mytest/myartifact/")
    assertFalse(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isFile)
    importProject(pom())
    assertTrue(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isFile)
  }

  @Test
  @Throws(Exception::class)
  fun testDownloadedFromRepositoryWithWrongAuthentificationLeadsToError() {
    val helper = MavenCustomRepositoryHelper(myDir, "local1", "remote")
    val remoteRepoPath = helper.getTestDataPath("remote")
    val localRepoPath = helper.getTestDataPath("local1")
    setupRemoteRepositoryServer(remoteRepoPath, object : BasicAuthenticator("/") {
      override fun checkCredentials(username: String?, password: String?): Boolean {
        return username == USERNAME && password == PASSWORD
      }
    })
    repositoryPath = localRepoPath
    val settingsXml = createProjectSubFile(
      "settings.xml",
      """
       <settings>
          <localRepository>$localRepoPath</localRepository>
          <servers>
            <server>
              <id>my-http-repository</id>
              <username>ANOTHER_USERNAME</username>
              <password>ANOTHER_PASSWORD</password>
            </server>
          </servers>
          
       </settings>
       """.trimIndent())

    mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)
    removeFromLocalRepository("org/mytest/myartifact/")
    assertFalse(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isFile)
    createProjectPom(pom())
    importProjectWithErrors()
    TestCase.assertEquals(1, myProjectsManager.rootProjects.size)
    TestCase.assertEquals("status code: 401, reason phrase: Unauthorized (401)",
                          myProjectsManager.rootProjects[0].problems.single { it.type == MavenProjectProblem.ProblemType.REPOSITORY }.description)

  }

  @Test
  fun `settings xml respected at the very start of the container`() {
    val helper = MavenCustomRepositoryHelper(myDir, "local1", "remote")
    val remoteRepoPath = helper.getTestDataPath("remote")
    val localRepoPath = helper.getTestDataPath("local1")
    setupRemoteRepositoryServer(remoteRepoPath, object : BasicAuthenticator("/") {
      override fun checkCredentials(username: String?, password: String?): Boolean {
        return username == USERNAME && password == PASSWORD
      }
    })
    repositoryPath = localRepoPath
    @Language(value = "XML") val settingsXmlText = """
       <settings>
          <localRepository>$localRepoPath</localRepository>
          <servers>
            <server>
              <id>artifacts</id>
              <username>$USERNAME</username>
              <password>$PASSWORD</password>
            </server>
          </servers>
          
          <profiles>
            <profile>
              <id>artifacts</id>
              <repositories>
                <repository>
                  <id>artifacts</id>
                  <url>$myUrl</url>
                </repository>
              </repositories>
              <pluginRepositories>
                <pluginRepository>
                  <id>artifacts</id>
                  <url>$myUrl</url>
                </pluginRepository>
              </pluginRepositories>
            </profile>
          </profiles>
          <activeProfiles>
              <activeProfile>artifacts</activeProfile>
          </activeProfiles>
       </settings>
       """.trimIndent()

    val settingsXml = createProjectSubFile("settings.xml", settingsXmlText)

    mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)

    createProjectPom("""<groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """);

    val extensionsXml = createProjectSubFile(".mvn/extensions.xml", """
    <extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
      <extension>
        <groupId>org.mytest</groupId>
        <artifactId>myartifact</artifactId>
        <version>1.0</version>
      </extension>
    </extensions>
    """.trimIndent());

    Registry.get("maven.server.debug").setValue(false)
    removeFromLocalRepository("org/mytest/myartifact/")
    assertFalse(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isFile)
    val embedderWrapper = MavenServerManager.getInstance().createEmbedder(myProject, true, myProjectRoot.toNioPath().toString())
    val embedder = embedderWrapper.getEmbedder()
    assertTrue("Embedder should be remote object: got class ${embedder.javaClass.name}", embedder.javaClass.name.contains("\$Proxy"))
    assertFalse(embedder.javaClass.isAssignableFrom(MisconfiguredPlexusDummyEmbedder::class.java))


  }

  private fun setupRemoteRepositoryServer(repoPath: String, authenticator: Authenticator? = null) {
    val repo = File(repoPath)
    val httpContext = myServer.createContext("/") { exchange ->
      val path = exchange.requestURI.path
      val file = File(repo, path)
      if (file.isDirectory) {
        exchange.responseHeaders.add("Content-Type", "text/html")
        exchange.sendResponseHeaders(200, 0)
        val list = java.lang.String.join(",\n", *file.list())
        exchange.responseBody.write(list.toByteArray(StandardCharsets.UTF_8))
      }
      else if (file.isFile) {
        exchange.responseHeaders.add("Content-Type", "application/octet-stream")
        exchange.sendResponseHeaders(200, 0)
        StreamUtil.copy(FileInputStream(file), exchange.responseBody)
      }
      else {
        exchange.sendResponseHeaders(404, -1)
      }
      exchange.close()
    }
    authenticator?.let { httpContext.authenticator = it }
  }

  fun pom() = """
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
                       <repositories>
                           <repository>
                             <id>my-http-repository</id>
                             <name>my-http-repository</name>
                             <url>${myUrl}</url>
                           </repository>
                       </repositories>
                       """.trimIndent()

  companion object {
    private const val LOCALHOST = "127.0.0.1"
    private const val USERNAME = "myUsername"
    private const val PASSWORD = "myPassword"
  }
}
