// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.impl.source.xml.XmlFileImpl
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.assertResolved
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.findPsiFile
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.refreshFiles
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenRelativePathResolutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    initialPom = MavenDomTestFixture.DEFAULT_POM,
    indices = MavenDomTestFixtureIndices("local1", listOf("local2")),
  )

  @Test
  fun testParentRelativePathOutsideProjectRoot() = runBlocking {
    val file = maven.repositoryHelper.getTestData("local1/org/example/example/1.0/example-1.0.pom")

    val relativePath = maven.projectRoot.toNioPath().relativize(file).toString()
    val relativePathUnixSeparator = relativePath.replace("\\\\".toRegex(), "/")

    val pom = maven.createProjectPom("""<groupId>test</groupId>
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

    maven.refreshFiles(listOf(pom))
    maven.fixture.configureFromExistingVirtualFile(pom)

    val resolved = readAction { maven.fixture.getElementAtCaret() }
    assertTrue(resolved is XmlFileImpl)
    val f = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
    val parentPsi = maven.findPsiFile(f)
    maven.assertResolved(maven.projectPom, parentPsi)
    assertSame(parentPsi, resolved)
  }


  @Test
  fun testParentRelativePathOutsideProjectRootWithDir() = runBlocking {
    val file = maven.repositoryHelper.getTestData("local1/org/example/example/1.0/pom.xml")

    val parentFile = file.parent

    val relativePath = maven.projectRoot.toNioPath().relativize(parentFile).toString()
    val relativePathUnixSeparator = relativePath.replace("\\\\".toRegex(), "/")

    val pom = maven.createProjectPom("""<groupId>test</groupId>
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
    maven.refreshFiles(listOf(pom))
    maven.fixture.configureFromExistingVirtualFile(pom)

    val resolved = readAction { maven.fixture.getElementAtCaret() }
    assertTrue(resolved is XmlFileImpl)
    val f = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
    val parentPsi = maven.findPsiFile(f)
    maven.assertResolved(maven.projectPom, parentPsi)
    assertSame(parentPsi, resolved)
  }
}
