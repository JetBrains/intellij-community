// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.IncorrectOperationException
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.junit.Test

class MavenModelReadingAndWritingTest : MavenMultiVersionImportingTestCase() {
  override fun runInDispatchThread() = true

  override fun setUp() = runBlocking {
    super.setUp()

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
  }

  @Test
  fun testReading() = runBlocking {
    val model = domModel

    assertEquals("test", model!!.getGroupId().getStringValue())
    assertEquals("project", model.getArtifactId().getStringValue())
    assertEquals("1", model.getVersion().getStringValue())
  }

  @Test
  fun testWriting() = runBlocking {
    CommandProcessor.getInstance().executeCommand(project, {
      ApplicationManager.getApplication().runWriteAction {
        val model = domModel
        model!!.getGroupId().setStringValue("foo")
        model.getArtifactId().setStringValue("bar")
        model.getVersion().setStringValue("baz")
        formatAndSaveProjectPomDocument()
      }
    }, null, null)

    UsefulTestCase.assertSameLines("""
                      <?xml version="1.0"?>${'\r'}
                      <project xmlns="http://maven.apache.org/POM/4.0.0"${'\r'}
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"${'\r'}
                               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">${'\r'}
                          <modelVersion>4.0.0</modelVersion>${'\r'}
                          <groupId>foo</groupId>${'\r'}
                          <artifactId>bar</artifactId>${'\r'}
                          <version>baz</version>${'\r'}
                      </project>
                      """.trimIndent(),
                                   VfsUtil.loadText(projectPom))
  }

  @Test
  fun testAddingADependency() = runBlocking {
    CommandProcessor.getInstance().executeCommand(project, {
      ApplicationManager.getApplication().runWriteAction {
        val model = domModel
        val d = model!!.getDependencies().addDependency()
        d.getGroupId().setStringValue("group")
        d.getArtifactId().setStringValue("artifact")
        d.getVersion().setStringValue("version")
        formatAndSaveProjectPomDocument()
      }
    }, null, null)

    UsefulTestCase.assertSameLines("""
                      <?xml version="1.0"?>${'\r'}
                      <project xmlns="http://maven.apache.org/POM/4.0.0"${'\r'}
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"${'\r'}
                               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">${'\r'}
                          <modelVersion>4.0.0</modelVersion>${'\r'}
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
                      """.trimIndent(), VfsUtil.loadText(projectPom))
  }

  private val domModel: MavenDomProjectModel?
    get() = MavenDomUtil.getMavenDomProjectModel(project, projectPom)

  private fun formatAndSaveProjectPomDocument() {
    try {
      val psiFile = PsiManager.getInstance(project).findFile(projectPom)
      CodeStyleManager.getInstance(project).reformat(psiFile!!)
      val d = FileDocumentManager.getInstance().getDocument(projectPom)
      FileDocumentManager.getInstance().saveDocument(d!!)
    }
    catch (e: IncorrectOperationException) {
      throw RuntimeException(e)
    }
  }
}
