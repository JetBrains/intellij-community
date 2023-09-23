// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.maven.testFramework.utils.MavenHttpRepositoryServerFixture
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.RunAll.Companion.runAll
import com.intellij.util.ThrowableRunnable
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.model.MavenProjectProblem
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.server.MisconfiguredPlexusDummyEmbedder
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class MavenRepositoriesDownloadingTest : MavenMultiVersionImportingTestCase() {
  override fun runInDispatchThread() = false

  private val httpServerFixture = MavenHttpRepositoryServerFixture()
  private lateinit var myUrl: String

  public override fun setUp() {
    super.setUp()
    httpServerFixture.setUp()
    myUrl = httpServerFixture.url()
  }


  public override fun tearDown() {
    runAll(
      ThrowableRunnable<Throwable> {
        httpServerFixture.tearDown()
      },
      ThrowableRunnable<Throwable> { super.tearDown() }
    )
  }

  @Test
  fun testDownloadedFromRepository() = runBlocking {
    val helper = MavenCustomRepositoryHelper(myDir, "local1", "remote")
    val remoteRepoPath = helper.getTestDataPath("remote")
    val localRepoPath = helper.getTestDataPath("local1")
    httpServerFixture.startRepositoryFor(remoteRepoPath)
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
    importProjectAsync(pom())
    assertTrue(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isFile)
  }

  @Test
  fun testPluginDownloadedFromRepository() = runBlocking {
    val helper = MavenCustomRepositoryHelper(myDir, "local1", "remote")
    val remoteRepoPath = helper.getTestDataPath("remote")
    val localRepoPath = helper.getTestDataPath("local1")
    httpServerFixture.startRepositoryFor(remoteRepoPath)
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
    importProjectAsync(pomPlugins())
    projectsManager.waitForPluginResolution()
    assertTrue(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isFile)
  }


  @Test
  fun testDownloadedFromRepositoryWithAuthentification() = runBlocking {
    val helper = MavenCustomRepositoryHelper(myDir, "local1", "remote")
    val remoteRepoPath = helper.getTestDataPath("remote")
    val localRepoPath = helper.getTestDataPath("local1")
    httpServerFixture.startRepositoryFor(File(remoteRepoPath), USERNAME, PASSWORD)
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
    importProjectAsync(pom())
    assertTrue(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isFile)
  }

  @Test
  fun testDownloadedFromRepositoryWithWrongAuthentificationLeadsToError() = runBlocking {
    assumeTrue(isWorkspaceImport)
    val helper = MavenCustomRepositoryHelper(myDir, "local1", "remote")
    val remoteRepoPath = helper.getTestDataPath("remote")
    val localRepoPath = helper.getTestDataPath("local1")
    httpServerFixture.startRepositoryFor(File(remoteRepoPath), USERNAME, PASSWORD)
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
    doImportProjects(listOf(myProjectPom), false)
    TestCase.assertEquals(1, projectsManager.rootProjects.size)
    TestCase.assertEquals("status code: 401, reason phrase: Unauthorized (401)",
                          projectsManager.rootProjects[0].problems.single { it.type == MavenProjectProblem.ProblemType.REPOSITORY }.description)
  }

  @Test
  fun `settings xml respected at the very start of the container`() = runBlocking {
    val helper = MavenCustomRepositoryHelper(myDir, "local1", "remote")
    val remoteRepoPath = helper.getTestDataPath("remote")
    val localRepoPath = helper.getTestDataPath("local1")
    httpServerFixture.startRepositoryFor(File(remoteRepoPath), USERNAME, PASSWORD)
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
                       """)

    createProjectSubFile(".mvn/extensions.xml", """
    <extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
      <extension>
        <groupId>org.mytest</groupId>
        <artifactId>myartifact</artifactId>
        <version>1.0</version>
      </extension>
    </extensions>
    """.trimIndent())

    Registry.get("maven.server.debug").setValue(false)
    removeFromLocalRepository("org/mytest/myartifact/")
    assertFalse(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isFile)
    val embedderWrapper = MavenServerManager.getInstance().createEmbedder(myProject, true, myProjectRoot.toNioPath().toString())
    val embedder = embedderWrapper.getEmbedder()
    assertTrue("Embedder should be remote object: got class ${embedder.javaClass.name}", embedder.javaClass.name.contains("\$Proxy"))
    assertFalse(embedder.javaClass.isAssignableFrom(MisconfiguredPlexusDummyEmbedder::class.java))
  }

  @Test
  fun testWithDependencyLastUpdatedWithErrorNoForce() = runBlocking {
    doLastUpdatedTest(false, pom()) {
      TestCase.assertEquals(1, projectsManager.rootProjects.size)
      TestCase.assertEquals("Unresolved dependency: 'org.mytest:myartifact:jar:1.0'",
                            projectsManager.rootProjects[0].problems.single { it.type == MavenProjectProblem.ProblemType.DEPENDENCY }.description)
    }
  }

  @Test
  fun testWithPluginLastUpdatedWithErrorNoForce() = runBlocking {
    doLastUpdatedTest(false, pomPlugins()) {
      TestCase.assertEquals(1, projectsManager.rootProjects.size)
      TestCase.assertEquals("Unresolved plugin: 'org.mytest:myartifact:1.0'",
                            projectsManager.rootProjects[0].problems.single { it.type == MavenProjectProblem.ProblemType.DEPENDENCY }.description)

    }
  }


  @Test
  fun testWithDependencyLastUpdatedWithErrorForceUpdate() = runBlocking {
    doLastUpdatedTest(true, pom()) {
      TestCase.assertEquals(1, projectsManager.rootProjects.size)
      TestCase.assertEquals(0, projectsManager.rootProjects[0].problems.size)
    }
  }

  @Test
  fun testWithPluginLastUpdatedWithErrorForceUpdate() = runBlocking {
    doLastUpdatedTest(true, pomPlugins()) {
      TestCase.assertEquals(1, projectsManager.rootProjects.size)
      TestCase.assertEquals(0, projectsManager.rootProjects[0].problems.size)
    }
  }

  private fun doLastUpdatedTest(updateSnapshots: Boolean, pomContent: String, checks: () -> Unit) = runBlocking {
    val helper = MavenCustomRepositoryHelper(myDir, "local1", "remote")
    val remoteRepoPath = helper.getTestDataPath("remote")
    val localRepoPath = helper.getTestDataPath("local1")
    httpServerFixture.startRepositoryFor(remoteRepoPath)
    repositoryPath = localRepoPath
    val settingsXml = createProjectSubFile(
      "settings.xml",
      """
       <settings>
          <localRepository>$localRepoPath</localRepository>
       </settings>
       """.trimIndent())
    mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)
    projectsManager.forceUpdateSnapshots = updateSnapshots
    mavenGeneralSettings.isAlwaysUpdateSnapshots = updateSnapshots

    removeFromLocalRepository("org/mytest/myartifact/")
    val lastUpdatedText =
      "#NOTE: This is a Maven Resolver internal implementation file, its format can be changed without prior notice\n" +
      "${myUrl.replace(":", "\\:")}/.error=\n" +
      "${myUrl.replace(":", "\\:")}/.lastUpdated=${System.currentTimeMillis()}\n"
    val dir = helper.getTestData("local1/org/mytest/myartifact/1.0")
    dir.mkdirs()

    File(dir, "myartifact-1.0.jar.lastUpdated").writeText(lastUpdatedText)
    File(dir, "myartifact-1.0.pom.lastUpdated").writeText(lastUpdatedText)

    importProjectAsync(pomContent)
    projectsManager.waitForPluginResolution()
    checks()
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

  fun pomPlugins() = """
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                          <plugins>
                            <plugin>
                               <groupId>org.mytest</groupId>
                               <artifactId>myartifact</artifactId>
                               <version>1.0</version>
                            </plugin>
                          </plugins>
                       </build>
                       <pluginRepositories>
                         <pluginRepository>
                           <id>artifacts</id>
                           <url>$myUrl</url>
                         </pluginRepository>
                       </pluginRepositories>
                       """.trimIndent()

  companion object {
    private const val USERNAME = "myUsername"
    private const val PASSWORD = "myPassword"
  }
}
