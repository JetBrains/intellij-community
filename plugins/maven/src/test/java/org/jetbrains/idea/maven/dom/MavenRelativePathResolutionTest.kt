// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

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
    val file = myIndicesFixture!!.repositoryHelper.getTestData("local1/org/example/1.0/example-1.0.pom")


    val relativePathUnixSeparator =
      FileUtil.getRelativePath(File(myProjectRoot.getPath()), file)!!.replace("\\\\".toRegex(), "/")

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

    myFixture.configureFromExistingVirtualFile(pom)
    val resolved = myFixture.getElementAtCaret()
    assertTrue(resolved is XmlFileImpl)
    val f = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.path)
    val parentPsi = findPsiFile(f)
    assertResolved(myProjectPom, parentPsi)
    assertSame(parentPsi, resolved)
  }


  @Test
  fun testParentRelativePathOutsideProjectRootWithDir() = runBlocking {
    val file = myIndicesFixture!!.repositoryHelper.getTestData("local1/org/example/1.0/pom.xml")

    val parentFile = file.getParentFile()


    val relativePathUnixSeparator =
      FileUtil.getRelativePath(File(myProjectRoot.getPath()), parentFile)!!.replace("\\\\".toRegex(), "/")

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

    myFixture.configureFromExistingVirtualFile(pom)
    val resolved = myFixture.getElementAtCaret()
    assertTrue(resolved is XmlFileImpl)
    val f = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.path)
    val parentPsi = findPsiFile(f)
    assertResolved(myProjectPom, parentPsi)
    assertSame(parentPsi, resolved)
  }
}
