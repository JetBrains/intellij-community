// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal class MavenSettingsCacheTest : MavenMultiVersionImportingTestCase() {

  override fun setUp() = runBlocking {
    super.setUp()
    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1</version>
""")
  }

  @Test
  fun testSettingsCacheReadDataFromConfigWithInterpolation() = runBlocking {
    createProjectSubFile(".mvn/maven.config", "-Dmaven.local=mavenLocal\n" +
                                              "-s.mvn/settings.xml\n")
    createProjectSubFile(".mvn/settings.xml",
                         """<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
      	<localRepository>${'$'}{maven.local}</localRepository>
      </settings>
    """.trimIndent())

    mavenGeneralSettings.setUserSettingsFile("")
    mavenGeneralSettings.setLocalRepository("")
    MavenSettingsCache.getInstance(project).reloadAsync()
    //assertTrue("Should create a new directory", projectPath.resolve("mavenLocal").isDirectory())
    assertEquals(projectPath.resolve("mavenLocal"),
                 MavenSettingsCache.getInstance(project).getEffectiveUserLocalRepo());
  }
}
