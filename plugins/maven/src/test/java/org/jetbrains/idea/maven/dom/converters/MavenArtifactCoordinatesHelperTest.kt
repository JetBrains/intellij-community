// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.util.xml.impl.ConvertContextFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.converters.MavenArtifactCoordinatesHelper.getMavenId
import org.junit.Test

class MavenArtifactCoordinatesHelperTest : MavenDomTestCase() {
  override fun runInDispatchThread() = true

  @Test
  fun testGetPluginVersionFromParentPluginManagement() = runBlocking {
    val parentFile = createProjectPom("""
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
    val m1File = createModulePom("m1", """
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
    importProjectAsync()

    val pluginVersion = "1.0.0"

    val mavenModel = MavenDomUtil.getMavenDomProjectModel(project, m1File)
    val coords = mavenModel!!.getBuild().getPlugins().getPlugins()[0]
    val converterContext = ConvertContextFactory.createConvertContext(mavenModel)

    val mavenId = getMavenId(coords, converterContext)

    assertEquals(pluginVersion, mavenId.version)
  }
}
