package org.jetbrains.idea.maven.dom;

public class MavenPropertyRenameTest extends MavenDomTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>module1</artifactId>" +
                  "<version>1</version>");
  }

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

  public void testDoNotRenameModelProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>1</version>" +

                     "<nam<caret>e>foo</name>" +
                     "<description>${project.name}</description>");

    assertCannotRename();
  }

  public void testDoNotRenameModelPropertiesFromReference() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>1</version>" +

                     "<name>foo</name>" +
                     "<description>${proje<caret>ct.name}</description>");

    assertCannotRename();
  }

  public void testDoNotRenameModelPropertiesTag() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>1</version>" +

                     "<name>foo</name>" +
                     "<properti<caret>es></properties>");

    assertCannotRename();
  }
}
