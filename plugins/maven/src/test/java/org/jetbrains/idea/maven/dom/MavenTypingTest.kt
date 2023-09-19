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

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Test

class MavenTypingTest : MavenDomTestCase() {
  @Test
  fun testTypingOpenBrace() {
    createProjectPom("""
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
  fun testTypingOpenBraceInsideOtherBrace() {
    createProjectPom("""
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
  fun testTypingOpenBraceWithExistingClosedBrace() {
    createProjectPom("""
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
  fun testTypingOpenBraceBeforeChar() {
    createProjectPom("""
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
  fun testTypingOpenBraceBeforeWhitespace() {
    createProjectPom("""
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
  fun testTypingOpenBraceWithoutDollar() {
    createProjectPom("""
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
  fun testTypingOpenBraceInTheEndOfFile() {
    val f = createProjectSubFile("pom.xml",
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
  fun testTypingOpenBraceInsideTag() {
    createProjectPom("""
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
  fun testDoNotHandleNonMavenFiles() {
    val f = createProjectSubFile("foo.xml", "$<caret>")

    assertTypeResultInRegularFile(f, '{', "\${<caret>")
  }

  @Test
  fun testWorksInFilteredResources() {
    createProjectSubDir("res")

    importProject("""
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

    val f = createProjectSubFile("res/foo.properties",
                                 "foo=$<caret>")

    assertTypeResultInRegularFile(f, '{', "foo=\${<caret>}")
  }

  @Test
  fun testDoesNotWorInNotFilteredResources() {
    createProjectSubDir("res")

    importProject("""
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

    val f = createProjectSubFile("res/foo.properties",
                                 "foo=$<caret>")

    assertTypeResultInRegularFile(f, '{', "foo=\${<caret>")
  }

  @Test
  fun testDeletingOpenBrace() {
    createProjectPom("""
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
  fun testDeletingOpenBraceWithTextInside() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <name>${'$'}{<caret>foo}</name>
                       """.trimIndent())

    assertBackspaceResult("""
                            <groupId>test</groupId>
                            <artifactId>project</artifactId>
                            <version>1</version>
                            <name>${'$'}<caret>foo}</name>
                            """.trimIndent())
  }

  @Test
  fun testDeletingOpenBraceWithoutClosed() {
    createProjectPom("""
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
  fun testDoNotHandleDeletionInsideRegularFile() {
    val f = createProjectSubFile("foo.html", "\${<caret>}")
    assertBackspaceResultInRegularFile(f, "$<caret>}")
  }

  @Test
  fun testDeletingInTheEndOfFile() {
    val f = createProjectSubFile("pom.xml",
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

  private fun assertTypeResult(c: Char, xml: String) {
    assertTypeResultInRegularFile(myProjectPom, c, createPomXml(xml))
  }

  private fun assertTypeResultInRegularFile(f: VirtualFile, c: Char, expected: String) {
    type(f, c)
    myFixture.checkResult(expected)
  }

  private fun assertBackspaceResult(xml: String) {
    assertTypeResult('\b', xml)
  }

  private fun assertBackspaceResultInRegularFile(f: VirtualFile, content: String) {
    assertTypeResultInRegularFile(f, '\b', content)
  }
}
