// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.xml.impl.ConvertContextFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.converters.MavenArtifactCoordinatesHelper.getMavenId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenArtifactCoordinatesHelperTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testGetPluginVersionFromParentPluginManagement() = runBlocking {
    val parentFile = maven.createProjectPom("""
                <groupId>group</groupId>
                <artifactId>parent</artifactId>
                <version>1</version>
                <packaging>pom</packaging>
                <build>
                  <pluginManagement>
                    <plugins>
                      <plugin>
                        <groupId>plugin-group</groupId>
                        <artifactId>plugin-artifact-id</artifactId>
                        <version>1.0.0</version>
                      </plugin>
                    </plugins>
                  </pluginManagement>
                </build>
                """.trimIndent())
    val m1File = maven.createModulePom("m1", """
                <artifactId>m1</artifactId>
                <version>1</version>
                <parent>
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                </parent>
                <build>
                  <plugins>
                    <plugin>
                      <groupId>plugin-group</groupId>
                      <artifactId>plugin-artifact-id</artifactId>
                    </plugin>
                  </plugins>
                </build>
                """.trimIndent())
    maven.importProjectAsync()

    val pluginVersion = "1.0.0"

    val mavenId = readAction {
      val mavenModel = MavenDomUtil.getMavenDomProjectModel(maven.project, m1File)
      val coords = mavenModel!!.getBuild().getPlugins().getPlugins()[0]
      val converterContext = ConvertContextFactory.createConvertContext(mavenModel)
      getMavenId(coords, converterContext)
    }

    assertEquals(pluginVersion, mavenId.version)
  }
}
