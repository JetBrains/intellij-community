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

import com.intellij.openapi.vfs.VfsUtilCore;
import org.junit.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenPropertyRenameTest extends MavenDomTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>module1</artifactId>" +
                  "<version>1</version>");
  }

  @Test
  public void testRenamingPropertyTag() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>1</version>" +

                     "<name>${foo}</name>" +
                     "<properties>" +
                     "  <f<caret>oo>value</foo>" +
                     "</properties>");

    assertRenameResult("xxx",
                       "<groupId>test</groupId>" +
                       "<artifactId>module1</artifactId>" +
                       "<version>1</version>" +

                       "<name>${xxx}</name>" +
                       "<properties>" +
                       "  <xxx>value</xxx>" +
                       "</properties>");
  }

  @Test
  public void testDoNotRuinTextAroundTheReferenceWhenRenaming() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>1</version>" +

                     "<name>aaa${foo}bbb</name>" +
                     "<properties>" +
                     "  <f<caret>oo>value</foo>" +
                     "</properties>");

    assertRenameResult("xxx",
                       "<groupId>test</groupId>" +
                       "<artifactId>module1</artifactId>" +
                       "<version>1</version>" +

                       "<name>aaa${xxx}bbb</name>" +
                       "<properties>" +
                       "  <xxx>value</xxx>" +
                       "</properties>");
  }

  @Test
  public void testRenamingChangesTheReferenceAccordingly() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>1</version>" +

                     "<name>aaa${foo}bbb</name>" +
                     "<properties>" +
                     "  <f<caret>oo>value</foo>" +
                     "</properties>");

    assertRenameResult("xxxxx",
                       "<groupId>test</groupId>" +
                       "<artifactId>module1</artifactId>" +
                       "<version>1</version>" +

                       "<name>aaa${xxxxx}bbb</name>" +
                       "<properties>" +
                       "  <xxxxx>value</xxxxx>" +
                       "</properties>");

    assertRenameResult("xx",
                       "<groupId>test</groupId>" +
                       "<artifactId>module1</artifactId>" +
                       "<version>1</version>" +

                       "<name>aaa${xx}bbb</name>" +
                       "<properties>" +
                       "  <xx>value</xx>" +
                       "</properties>");
  }

  @Test
  public void testRenamingPropertyFromReference() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>1</version>" +

                     "<name>${f<caret>oo}</name>" +
                     "<properties>" +
                     "  <foo>value</foo>" +
                     "</properties>");

    assertRenameResult("xxx",
                       "<groupId>test</groupId>" +
                       "<artifactId>module1</artifactId>" +
                       "<version>1</version>" +

                       "<name>${xxx}</name>" +
                       "<properties>" +
                       "  <xxx>value</xxx>" +
                       "</properties>");
  }

  @Test
  public void testRenamingPropertyInResourceFile() throws Exception {
    final File actual = VfsUtilCore.virtualToIoFile(
      createProjectSubFile("src/main/resources/data.properties", "foo=test"));

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>1</version>" +

                     "<name>${f<caret>oo}</name>" +
                     "<properties>" +
                     "  <foo>value</foo>" +
                     "</properties>");

    assertRenameResult("xxx",
                       "<groupId>test</groupId>" +
                       "<artifactId>module1</artifactId>" +
                       "<version>1</version>" +

                       "<name>${xxx}</name>" +
                       "<properties>" +
                       "  <xxx>value</xxx>" +
                       "</properties>");

    assertThat(actual).hasContent("xxx=test");
  }

  @Test
  public void testDoNotRenameModelProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>1</version>" +

                     "<nam<caret>e>foo</name>" +
                     "<description>${project.name}</description>");

    assertCannotRename();
  }

  @Test
  public void testDoNotRenameModelPropertiesFromReference() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>1</version>" +

                     "<name>foo</name>" +
                     "<description>${proje<caret>ct.name}</description>");

    assertCannotRename();
  }

  @Test
  public void testDoNotRenameModelPropertiesTag() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>1</version>" +

                     "<name>foo</name>" +
                     "<properti<caret>es></properties>");

    assertCannotRename();
  }
}
