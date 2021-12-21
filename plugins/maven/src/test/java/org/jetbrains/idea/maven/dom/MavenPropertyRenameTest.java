// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom;

import com.intellij.maven.testFramework.MavenDomTestCase;
import org.junit.Test;

public class MavenPropertyRenameTest extends MavenDomTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
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
