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
package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.vfs.VirtualFile;

public class MavenTypingTest extends MavenDomTestCase {
  public void testTypingOpenBrace() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>$<caret></name>");

    assertTypeResult('{',
                     "<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<caret>}</name>");
  }

  public void testTypingOpenBraceInsideOtherBrace() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<caret></name>");

    assertTypeResult('{',
                     "<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${{<caret></name>");
  }

  public void testTypingOpenBraceWithExistingClosedBrace() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>$<caret>}</name>");

    assertTypeResult('{',
                     "<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<caret>}</name>");
  }

  public void testTypingOpenBraceBeforeChar() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>$<caret>foo</name>");

    assertTypeResult('{',
                     "<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<caret>foo</name>");
  }

  public void testTypingOpenBraceBeforeWhitespace() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>$<caret> foo</name>");

    assertTypeResult('{',
                     "<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<caret>} foo</name>");
  }

  public void testTypingOpenBraceWithoutDollar() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name><caret></name>");

    assertTypeResult('{',
                     "<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>{<caret></name>");
  }

  public void testTypingOpenBraceInTheEndOfFile() throws Exception {
    VirtualFile f = createProjectSubFile("pom.xml",
                                         "<project>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>project</artifactId>" +
                                         "  <version>1</version>" +
                                         "  <name>$<caret>");

    assertTypeResultInRegularFile(f, '{',
                                  "<project>" +
                                  "  <groupId>test</groupId>" +
                                  "  <artifactId>project</artifactId>" +
                                  "  <version>1</version>" +
                                  "  <name>${<caret>}");
  }

  public void testTypingOpenBraceInsideTagDoesNothing() {
    if (ignore()) return;

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<$<caret>name>");

    assertTypeResult('{',
                     "<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<${<caret>name>");
  }

  public void testDoNotHandleNonMavenFiles() throws Exception {
    VirtualFile f = createProjectSubFile("foo.xml", "$<caret>");

    assertTypeResultInRegularFile(f, '{', "${<caret>");
  }

  public void testWorksInFilteredResources() throws Exception {
    createProjectSubDir("res");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=$<caret>");

    assertTypeResultInRegularFile(f, '{', "foo=${<caret>}");
  }

  public void testDoesNotWorInNotFilteredResources() throws Exception {
    createProjectSubDir("res");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <filtering>false</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=$<caret>");

    assertTypeResultInRegularFile(f, '{', "foo=${<caret>");
  }

  public void testDeletingOpenBrace() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<caret>}</name>");

    assertBackspaceResult("<groupId>test</groupId>" +
                          "<artifactId>project</artifactId>" +
                          "<version>1</version>" +
                          "<name>$<caret></name>");
  }

  public void testDeletingOpenBraceWithTextInside() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<caret>foo}</name>");

    assertBackspaceResult("<groupId>test</groupId>" +
                          "<artifactId>project</artifactId>" +
                          "<version>1</version>" +
                          "<name>$<caret>foo}</name>");
  }

  public void testDeletingOpenBraceWithoutClosed() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<name>${<caret></name>");

    assertBackspaceResult("<groupId>test</groupId>" +
                          "<artifactId>project</artifactId>" +
                          "<version>1</version>" +
                          "<name>$<caret></name>");
  }

  public void testDoNotHandleDeletionInsideRegularFile() throws Exception {
    VirtualFile f = createProjectSubFile("foo.html", "${<caret>}");
    assertBackspaceResultInRegularFile(f, "$<caret>}");
  }

  public void testDeletingInTheEndOfFile() throws Exception {
    VirtualFile f = createProjectSubFile("pom.xml",
                                         "<project>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>project</artifactId>" +
                                         "  <version>1</version>" +
                                         "  <name>${<caret>");

    assertBackspaceResultInRegularFile(f,
                                       "<project>" +
                                       "  <groupId>test</groupId>" +
                                       "  <artifactId>project</artifactId>" +
                                       "  <version>1</version>" +
                                       "  <name>$<caret>");
  }

  private void assertTypeResult(char c, String xml) {
    assertTypeResultInRegularFile(myProjectPom, c, createPomXml(xml));
  }

  private void assertTypeResultInRegularFile(VirtualFile f, char c, String expected) {
    type(f, c);
    myFixture.checkResult(expected);
  }

  private void assertBackspaceResult(String xml) {
    assertTypeResult('\b', xml);
  }

  private void assertBackspaceResultInRegularFile(VirtualFile f, String content) {
    assertTypeResultInRegularFile(f, '\b', content);
  }
}
