// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.IncorrectOperationException
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenModelReadingAndWritingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp(): Unit = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
  }

  @Test
  fun testReading() = runBlocking {
    readAction {
      val model = domModel

      assertEquals("test", model!!.getGroupId().getStringValue())
      assertEquals("project", model.getArtifactId().getStringValue())
      assertEquals("1", model.getVersion().getStringValue())
    }
  }

  @Test
  fun testWriting() = runBlocking {
    writeCommandAction(maven.project, "") {
      val model = domModel
      model!!.getGroupId().setStringValue("foo")
      model.getArtifactId().setStringValue("bar")
      model.getVersion().setStringValue("baz")
      formatAndSaveProjectPomDocument()
    }

    UsefulTestCase.assertSameLines("""
                      <?xml version="1.0"?>${'\r'}
                      <project xmlns="http://maven.apache.org/POM/${maven.modelVersion}"${'\r'}
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"${'\r'}
                               xsi:schemaLocation="http://maven.apache.org/POM/${maven.modelVersion} http://maven.apache.org/xsd/maven-${maven.modelVersion}.xsd">${'\r'}
                          <modelVersion>${maven.modelVersion}</modelVersion>${'\r'}
                          <groupId>foo</groupId>${'\r'}
                          <artifactId>bar</artifactId>${'\r'}
                          <version>baz</version>${'\r'}
                      </project>
                      """.trimIndent(),
                                   VfsUtil.loadText(maven.projectPom))
  }

  @Test
  fun testAddingADependency() = runBlocking {
    writeCommandAction(maven.project, "") {
      val model = domModel
      val d = model!!.getDependencies().addDependency()
      d.getGroupId().setStringValue("group")
      d.getArtifactId().setStringValue("artifact")
      d.getVersion().setStringValue("version")
      formatAndSaveProjectPomDocument()
    }

    UsefulTestCase.assertSameLines("""
                      <?xml version="1.0"?>${'\r'}
                      <project xmlns="http://maven.apache.org/POM/${maven.modelVersion}"${'\r'}
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"${'\r'}
                               xsi:schemaLocation="http://maven.apache.org/POM/${maven.modelVersion} http://maven.apache.org/xsd/maven-${maven.modelVersion}.xsd">${'\r'}
                          <modelVersion>${maven.modelVersion}</modelVersion>${'\r'}
                          <groupId>test</groupId>${'\r'}
                          <artifactId>project</artifactId>${'\r'}
                          <version>1</version>${'\r'}
                          <dependencies>${'\r'}
                              <dependency>${'\r'}
                                  <groupId>group</groupId>${'\r'}
                                  <artifactId>artifact</artifactId>${'\r'}
                                  <version>version</version>${'\r'}
                              </dependency>${'\r'}
                          </dependencies>${'\r'}
                      </project>
                      """.trimIndent(), VfsUtil.loadText(maven.projectPom))
  }

  private val domModel: MavenDomProjectModel?
    get() = MavenDomUtil.getMavenDomProjectModel(maven.project, maven.projectPom)

  private fun formatAndSaveProjectPomDocument() {
    try {
      val psiFile = PsiManager.getInstance(maven.project).findFile(maven.projectPom)
      CodeStyleManager.getInstance(maven.project).reformat(psiFile!!)
      val d = FileDocumentManager.getInstance().getDocument(maven.projectPom)
      FileDocumentManager.getInstance().saveDocument(d!!)
    }
    catch (e: IncorrectOperationException) {
      throw RuntimeException(e)
    }
  }
}
