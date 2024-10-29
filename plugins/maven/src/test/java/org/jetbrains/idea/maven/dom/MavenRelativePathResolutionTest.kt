// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.impl.source.xml.XmlFileImpl
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

class MavenRelativePathResolutionTest : MavenDomWithIndicesTestCase() {
  override fun setUp() = runBlocking {
    super.setUp()
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
  }

  @Test
  fun testParentRelativePathOutsideProjectRoot() = runBlocking {
    val file = myIndicesFixture!!.repositoryHelper.getTestData("local1/org/example/example/1.0/example-1.0.pom")


    val relativePathUnixSeparator =
      FileUtil.getRelativePath(File(projectRoot.getPath()), file)!!.replace("\\\\".toRegex(), "/")

    val pom = createProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<parent>
  <groupId>org.example</groupId>
  <artifactId>example</artifactId>
  <version>1.0</version>
  <relativePath>
$relativePathUnixSeparator<caret></relativePath>
</parent>"""
    )

    refreshFiles(listOf(pom))
    fixture.configureFromExistingVirtualFile(pom)

    val resolved = readAction { fixture.getElementAtCaret() }
    assertTrue(resolved is XmlFileImpl)
    val f = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.path)
    val parentPsi = findPsiFile(f)
    assertResolved(projectPom, parentPsi)
    assertSame(parentPsi, resolved)
  }


  @Test
  fun testParentRelativePathOutsideProjectRootWithDir() = runBlocking {
    val file = myIndicesFixture!!.repositoryHelper.getTestData("local1/org/example/example/1.0/pom.xml")

    val parentFile = file.getParentFile()


    val relativePathUnixSeparator =
      FileUtil.getRelativePath(File(projectRoot.getPath()), parentFile)!!.replace("\\\\".toRegex(), "/")

    val pom = createProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<parent>
  <groupId>org.example</groupId>
  <artifactId>example</artifactId>
  <version>1.0</version>
  <relativePath>
$relativePathUnixSeparator<caret></relativePath>
</parent>"""
    )
    refreshFiles(listOf(pom))
    fixture.configureFromExistingVirtualFile(pom)

    val resolved = readAction { fixture.getElementAtCaret() }
    assertTrue(resolved is XmlFileImpl)
    val f = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.path)
    val parentPsi = findPsiFile(f)
    assertResolved(projectPom, parentPsi)
    assertSame(parentPsi, resolved)
  }
}
