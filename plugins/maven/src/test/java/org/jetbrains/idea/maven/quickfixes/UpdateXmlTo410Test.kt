// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.quickfixes

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.buildtool.quickfix.UpdateXmlsTo410
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.model.MavenConstants.MAVEN_4_XLMNS
import org.jetbrains.idea.maven.model.MavenConstants.MAVEN_4_XSD
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class UpdateXmlsTo410Test : LightJavaCodeInsightFixtureTestCase() {

  private lateinit var quickFix: UpdateXmlsTo410

  override fun setUp() {
    super.setUp()
    quickFix = UpdateXmlsTo410()
  }

  override fun runInDispatchThread(): Boolean {
    return false
  }

  fun testUpdateMavenXmlTo410() = runBlocking {
    val xmlContent = """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0">
        <modelVersion>4.0.0</modelVersion>
      </project>
    """.trimIndent()

    val xmlFile = myFixture.configureByText("pom.xml", xmlContent) as XmlFile
    val projectTag = readAction { xmlFile.document?.rootTag }

    val descriptor = mock(ProblemDescriptor::class.java)
    `when`(descriptor.psiElement).thenReturn(projectTag)

    writeCommandAction(project, quickFix.name) {
      quickFix.applyFix(project, descriptor)
    }

    readAction {
      val updatedProjectTag = xmlFile.document?.rootTag
      assertEquals(MavenConstants.MAVEN_4_XLMNS, updatedProjectTag?.getAttribute("xmlns")?.value)
      assertEquals("http://www.w3.org/2001/XMLSchema-instance", updatedProjectTag?.getAttribute("xmlns:xsi")?.value)
      assertEquals("$MAVEN_4_XLMNS $MAVEN_4_XSD", updatedProjectTag?.getAttribute("xsi:schemaLocation")?.value)
      assertEquals(MavenConstants.MODEL_VERSION_4_1_0,
                   updatedProjectTag?.findFirstSubTag("modelVersion")?.value?.text)
    }

  }
}