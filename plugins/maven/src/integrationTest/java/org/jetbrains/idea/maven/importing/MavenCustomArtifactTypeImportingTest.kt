// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEmpty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.MavenExecutionOptions
import org.jetbrains.idea.maven.utils.MavenLog
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenCustomArtifactTypeImportingTest(mavenVersion: String, modelVersion: String) {
  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    skipPluginResolution = false,
  )

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

  @BeforeEach
  fun setUp() {
    val myTestSyncViewManager = object : SyncViewManager(maven.project) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        if (event is OutputBuildEvent) MavenLog.LOG.warn(event.message)
      }
    }
    maven.project.replaceService(SyncViewManager::class.java, myTestSyncViewManager, maven.disposable)
  }

  @Test
  fun `should import dependency with custom plugin type`() = runBlocking {
    maven.projectsManager.generalSettings.outputLevel = MavenExecutionOptions.LoggingLevel.DEBUG

    //httpServerFixture.startProxyRepositoryForUrl("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")

    maven.importProjectAsync("""
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
    maven.assertModules("project")
    val project = maven.projectsManager.findProject(maven.projectPom)
    assertNotNull(project)
    assertEmpty(project.problems)
  }
}