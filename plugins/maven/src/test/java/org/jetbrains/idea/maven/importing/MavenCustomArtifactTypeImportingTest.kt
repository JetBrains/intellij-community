// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.MavenExecutionOptions
import org.jetbrains.idea.maven.utils.MavenLog
import org.junit.Test

class MavenCustomArtifactTypeImportingTest : MavenMultiVersionImportingTestCase() {

/*  private val httpServerFixture = MavenHttpRepositoryServerFixture()
  private lateinit var myUrl: String

  public override fun setUp() {
    super.setUp()
    httpServerFixture.setUp()
    myUrl = httpServerFixture.url()

    updateSettingsXml("""
      <mirrors>
        <mirror>
          <id>disable-central</id>
          <mirrorOf>central</mirrorOf>
          <url>file:///non-existent-repo</url>
        </mirror>
      </mirrors>
    """.trimIndent())
  }

  public override fun tearDown() {
    try {
      httpServerFixture.tearDown()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }*/

  public override fun setUp() {
    super.setUp()
    val myTestSyncViewManager = object : SyncViewManager(project) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        if (event is OutputBuildEvent) MavenLog.LOG.warn(event.message)
      }
    }
    project.replaceService(SyncViewManager::class.java, myTestSyncViewManager, testRootDisposable)
  }

  @Test
  fun `should import dependency with custom plugin type`() = runBlocking {
    projectsManager.generalSettings.outputLevel = MavenExecutionOptions.LoggingLevel.DEBUG

    //httpServerFixture.startProxyRepositoryForUrl("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")

    importProjectAsync("""
      <groupId>test</groupId>
    <artifactId>project</artifactId>
    <version>1.0</version>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.sonarsource.java</groupId>
            <artifactId>sonar-java-plugin</artifactId>
            <version>8.1.0.36477</version>
            <scope>provided</scope>
            <type>sonar-plugin</type>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.sonarsource.sonar-packaging-maven-plugin</groupId>
                <artifactId>sonar-packaging-maven-plugin</artifactId>
                <version>1.23.0.740</version>
                <extensions>true</extensions>
            </plugin>
        </plugins>
    </build>
""")
    /*
    <repositories>
      <repository>
        <id>my-http-repository</id>
        <name>my-http-repository</name>
        <url>${myUrl}</url>
      </repository>
    </repositories>
    <pluginRepositories>
      <pluginRepository>
        <id>artifacts</id>
        <url>$myUrl</url>
      </pluginRepository>
    </pluginRepositories>
     */
    assertModules("project")
    val project = projectsManager.findProject(projectPom)
    assertNotNull(project)
    assertEmpty(project!!.problems)
  }
}