// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEmpty
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.MavenExecutionOptions
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

  @Test
  fun `should import dependency with custom plugin type`() = runBlocking {
    maven.projectsManager.generalSettings.outputLevel = MavenExecutionOptions.LoggingLevel.DEBUG

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
    maven.assertModules("project")
    val project = maven.projectsManager.findProject(maven.projectPom)
    assertNotNull(project)
    assertEmpty(project.problems)
  }
}