// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.system.OS
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import kotlin.io.path.div

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenSettingsCacheTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
""")
  }

  @Test
  fun testSettingsCacheReadDataFromConfigWithInterpolation() = runBlocking {
    maven.createProjectSubFile(".mvn/maven.config", "-Dmaven.local=mavenLocal\n" +
                                              "-s.mvn/settings.xml\n")
    maven.createProjectSubFile(".mvn/settings.xml",
                         """<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
      	<localRepository>${'$'}{maven.local}</localRepository>
      </settings>
    """.trimIndent())

    val mavenGeneralSettings = maven.projectsManager.generalSettings
    mavenGeneralSettings.setUserSettingsFile("")
    mavenGeneralSettings.setLocalRepository("")
    MavenSettingsCache.getInstance(maven.project).reloadAsync()
    //assertTrue("Should create a new directory", projectPath.resolve("mavenLocal").isDirectory())
    assertEquals(maven.project.basePath!!.toNioPathOrNull()!!.resolve("mavenLocal"),
                 MavenSettingsCache.getInstance(maven.project).getEffectiveUserLocalRepo())
  }


  @Test
  fun testSettingsCacheReadDataFromConfigWithInterpolationOfEnvVariables() = runBlocking {
    val envVariableName = if (OS.CURRENT == OS.Windows) "USERNAME" else "USER"
    maven.createProjectSubFile(".mvn/maven.config", "-Dmaven.local=mavenLocal\n" +
                                              "-s.mvn/settings.xml\n")
    maven.createProjectSubFile(".mvn/settings.xml",
                         $$"""<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
      	<localRepository>${env.$$envVariableName}/${maven.local}</localRepository>
      </settings>
    """.trimIndent())

    val mavenGeneralSettings = maven.projectsManager.generalSettings
    mavenGeneralSettings.setUserSettingsFile("")
    mavenGeneralSettings.setLocalRepository("")
    MavenSettingsCache.getInstance(maven.project).reloadAsync()
    //assertTrue("Should create a new directory", projectPath.resolve("mavenLocal").isDirectory())
    assertEquals(maven.project.basePath!!.toNioPathOrNull()!! / System.getenv(envVariableName) / "mavenLocal",
                 MavenSettingsCache.getInstance(maven.project).getEffectiveUserLocalRepo())
  }
}
