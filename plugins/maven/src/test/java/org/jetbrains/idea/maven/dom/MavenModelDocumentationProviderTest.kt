// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.readAction
import com.intellij.psi.xml.XmlFile
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenModelDocumentationProviderTest : MavenDomTestCase() {

  @Test
  fun testModelVersionDocumentation() = runBlocking {
    createProjectPom(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        """.trimIndent())

    val provider = MavenModelDocumentationProvider()
    val psi = findPsiFile(projectPom) as XmlFile
    val doc = readAction {
      provider.generateDoc(psi.rootTag!!.findSubTags("modelVersion").single(), null)
    }

    assertEquals("Model property<br>project.modelVersion: <b>$modelVersion</b><br>Maven version: ${getActualVersion(myMavenVersion!!)}", doc)
  }
}