// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.findPsiFile
import com.intellij.maven.testFramework.fixtures.getActualMavenVersion
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.openapi.application.readAction
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenModelDocumentationProviderTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )

  @Test
  fun testModelVersionDocumentation() = runBlocking {
    maven.createProjectPom(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        """.trimIndent())

    val provider = MavenModelDocumentationProvider()
    val psi = maven.findPsiFile(maven.projectPom) as XmlFile
    val doc = readAction {
      provider.generateDoc(psi.rootTag!!.findSubTags("modelVersion").single(), null)
    }

    val modelVersion = maven.modelVersion
    assertEquals("Model property<br>project.modelVersion: <b>$modelVersion</b><br>Maven version: ${maven.getActualMavenVersion()}", doc)
  }
}