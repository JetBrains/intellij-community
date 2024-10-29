// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenCustomArtifactTypeImportingTest : MavenMultiVersionImportingTestCase() {


  @Test
  fun `should import dependency with custom plugin type`() = runBlocking {
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

    assertModules("project")
    val project = projectsManager.findProject(projectPom)
    assertNotNull(project)
    assertEmpty(project!!.problems)

  }
}