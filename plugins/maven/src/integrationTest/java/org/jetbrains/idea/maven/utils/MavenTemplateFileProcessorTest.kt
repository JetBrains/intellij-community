// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory
import com.intellij.platform.templates.SaveProjectAsTemplateAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
@RunInEdt
class MavenTemplateFileProcessorTest {
  private val tempDir = tempPathFixture()
  private val project = projectFixture(tempDir, openAfterCreation = true)

  @Suppress("unused") // required by codeInsightFixture
  private val module by project.moduleFixture(tempDir, addPathToSourceRoot = true)

  private val fixture by codeInsightFixture(project, tempDir)

  @AfterEach
  fun afterEach() {
    MavenServerManager.getInstance().closeAllConnectorsAndWait()
  }

  companion object {
    private const val TEXT = """<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.springapp</groupId>
    <artifactId>springapp</artifactId>
    <packaging>jar</packaging>
    <version>1.0-SNAPSHOT</version>
    <name>SpringApp</name>
</project>"""
  }

  @Test
  fun testProcessor() {
    val file = fixture.configureByText("pom.xml", TEXT)
    val s = MavenTemplateFileProcessor().encodeFileText(TEXT, file.virtualFile, project.get())
    assertEquals("""<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.springapp</groupId>
    <artifactId>${'$'}{IJ_PROJECT_NAME}</artifactId>
    <packaging>jar</packaging>
    <version>1.0-SNAPSHOT</version>
    <name>${'$'}{IJ_PROJECT_NAME}</name>
</project>""", s)
  }

  @Test
  fun testSaveAsTemplate() {
    val file = fixture.configureByText("pom.xml", TEXT)
    val map = SaveProjectAsTemplateAction.computeParameters(project.get(), true)
    map["com.springapp"] = ProjectTemplateParameterFactory.IJ_BASE_PACKAGE
    val content = SaveProjectAsTemplateAction.getEncodedContent(file.virtualFile, project.get(), map)
    assertEquals("""<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>${'$'}{IJ_BASE_PACKAGE}</groupId>
    <artifactId>${'$'}{IJ_PROJECT_NAME}</artifactId>
    <packaging>jar</packaging>
    <version>1.0-SNAPSHOT</version>
    <name>${'$'}{IJ_PROJECT_NAME}</name>
</project>""", content)
  }
}
