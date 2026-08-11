/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.dom

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.type
import org.jetbrains.idea.maven.utils.MavenLog
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenTypingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testTypingOpenBrace() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}<caret></name>
                       """.trimIndent())

    assertTypeResult('{',
                     """
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>}</name>
                       """.trimIndent())
  }

  @Test
  fun testTypingOpenBraceInsideOtherBrace() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret></name>
                       """.trimIndent())

    assertTypeResult('{',
                     """
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{{<caret></name>
                       """.trimIndent())
  }

  @Test
  fun testTypingOpenBraceWithExistingClosedBrace() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}<caret>}</name>
                       """.trimIndent())

    assertTypeResult('{',
                     """
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>}</name>
                       """.trimIndent())
  }

  @Test
  fun testTypingOpenBraceBeforeChar() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}<caret>foo</name>
                       """.trimIndent())

    assertTypeResult('{',
                     """
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>foo</name>
                       """.trimIndent())
  }

  @Test
  fun testTypingOpenBraceBeforeWhitespace() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}<caret> foo</name>
                       """.trimIndent())

    assertTypeResult('{',
                     """
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>} foo</name>
                       """.trimIndent())
  }

  @Test
  fun testTypingOpenBraceWithoutDollar() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name><caret></name>
                       """.trimIndent())

    assertTypeResult('{',
                     """
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>{<caret></name>
                       """.trimIndent())
  }

  @Test
  fun testTypingOpenBraceInTheEndOfFile() = runBlocking {
    val f = maven.createProjectSubFile("pom.xml",
                                 """
                                           <project>
                                             <groupId>test</groupId>
                                             <artifactId>project</artifactId>
                                             <version>1</version>
                                             <name>${'$'}<caret>
                                             """.trimIndent())

    assertTypeResultInRegularFile(f, '{',
                                  """
                                    <project>
                                      <groupId>test</groupId>
                                      <artifactId>project</artifactId>
                                      <version>1</version>
                                      <name>${'$'}{<caret>}
                                      """.trimIndent())
  }

  @Test
  fun testTypingOpenBraceInsideTag() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <${'$'}<caret>name>
                       """.trimIndent())

    assertTypeResult('{',
                     """
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <${'$'}{<caret>}name>
                       """.trimIndent())
  }

  @Test
  fun testDoNotHandleNonMavenFiles() = runBlocking {
    val f = maven.createProjectSubFile("foo.xml", "$<caret>")

    assertTypeResultInRegularFile(f, '{', "\${<caret>")
  }

  @Test
  fun testWorksInFilteredResources() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>true</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "foo=$<caret>")

    assertTypeResultInRegularFile(f, '{', "foo=\${<caret>}")
  }

  @Test
  fun testDoesNotWorInNotFilteredResources() = runBlocking {
    maven.createProjectSubDir("res")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource>
                          <directory>res</directory>
                          <filtering>false</filtering>
                        </resource>
                      </resources>
                    </build>
                    """.trimIndent())

    val f = maven.createProjectSubFile("res/foo.properties",
                                 "foo=$<caret>")

    assertTypeResultInRegularFile(f, '{', "foo=\${<caret>")
  }

  @Test
  fun testDeletingOpenBrace() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>}</name>
                       """.trimIndent())

    assertBackspaceResult("""
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                            <name>${'$'}<caret></name>
                            """.trimIndent())
  }

  @Test
  fun testDeletingOpenBraceWithTextInside() = runBlocking {
    maven.createProjectPom($$"""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${<caret>foo}</name>
                       """.trimIndent())

    assertBackspaceResult("""
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                            <name>$<caret>foo}</name>
                            """.trimIndent())
  }

  @Test
  fun testDeletingOpenBraceWithoutClosed() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret></name>
                       """.trimIndent())

    assertBackspaceResult("""
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                            <name>${'$'}<caret></name>
                            """.trimIndent())
  }

  @Test
  fun testDoNotHandleDeletionInsideRegularFile() = runBlocking {
    val f = maven.createProjectSubFile("foo.html", "\${<caret>}")
    assertBackspaceResultInRegularFile(f, "$<caret>}")
  }

  @Test
  fun testDeletingInTheEndOfFile() = runBlocking {
    val f = maven.createProjectSubFile("pom.xml",
                                 """
                                           <project>
                                             <groupId>test</groupId>
                                             <artifactId>project</artifactId>
                                             <version>1</version>
                                             <name>${'$'}{<caret>
                                           """.trimIndent())

    assertBackspaceResultInRegularFile(f,
                                       """
                                         <project>
                                           <groupId>test</groupId>
                                           <artifactId>project</artifactId>
                                           <version>1</version>
                                           <name>${'$'}<caret>
                                         """.trimIndent())
  }

  private suspend fun assertTypeResult(c: Char, xml: String) {
    assertTypeResultInRegularFile(maven.projectPom, c, maven.createPomXml(xml))
  }

  private suspend fun assertTypeResultInRegularFile(f: VirtualFile, c: Char, expected: String) {
    val content = String(f.contentsToByteArray())
    MavenLog.LOG.warn("Typing '$c' in file $f:\n$content")
    maven.type(f, c)
    maven.fixture.checkResult(expected)
  }

  private suspend fun assertBackspaceResult(xml: String) {
    assertTypeResult('\b', xml)
  }

  private suspend fun assertBackspaceResultInRegularFile(f: VirtualFile, content: String) {
    assertTypeResultInRegularFile(f, '\b', content)
  }
}
