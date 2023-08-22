// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import org.junit.Test;

import java.io.File;

public class MavenRelativePathResolutionTest extends MavenDomWithIndicesTestCase {


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);
  }

  @Test
  public void testParentRelativePathOutsideProjectRoot() throws Exception {

    File file = myIndicesFixture.getRepositoryHelper().getTestData("local1/org/example/1.0/example-1.0.pom");


    String relativePathUnixSeparator =
      FileUtil.getRelativePath(new File(myProjectRoot.getPath()), file).replaceAll("\\\\", "/");

    VirtualFile pom = createProjectPom("<groupId>test</groupId>\n" +
                                       "<artifactId>project</artifactId>\n" +
                                       "<version>1</version>\n" +
                                       "<parent>\n" +
                                       "  <groupId>org.example</groupId>\n" +
                                       "  <artifactId>example</artifactId>\n" +
                                       "  <version>1.0</version>\n" +
                                       "  <relativePath>\n" + relativePathUnixSeparator + "<caret></relativePath>\n" +
                                       "</parent>"
    );

    myFixture.configureFromExistingVirtualFile(pom);
    PsiElement resolved = myFixture.getElementAtCaret();
    assertTrue(resolved instanceof XmlFileImpl);
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.getPath());
    PsiFile parentPsi = findPsiFile(f);
    assertResolved(myProjectPom, parentPsi);
    assertSame(parentPsi, resolved);
  }


  @Test 
  public void testParentRelativePathOutsideProjectRootWithDir() throws Exception {
    File file = myIndicesFixture.getRepositoryHelper().getTestData("local1/org/example/1.0/pom.xml");

    File parentFile = file.getParentFile();


    String relativePathUnixSeparator =
      FileUtil.getRelativePath(new File(myProjectRoot.getPath()), parentFile).replaceAll("\\\\", "/");

    VirtualFile pom = createProjectPom("<groupId>test</groupId>\n" +
                                       "<artifactId>project</artifactId>\n" +
                                       "<version>1</version>\n" +
                                       "<parent>\n" +
                                       "  <groupId>org.example</groupId>\n" +
                                       "  <artifactId>example</artifactId>\n" +
                                       "  <version>1.0</version>\n" +
                                       "  <relativePath>\n" + relativePathUnixSeparator + "<caret></relativePath>\n" +
                                       "</parent>"
    );

    myFixture.configureFromExistingVirtualFile(pom);
    PsiElement resolved = myFixture.getElementAtCaret();
    assertTrue(resolved instanceof XmlFileImpl);
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.getPath());
    PsiFile parentPsi = findPsiFile(f);
    assertResolved(myProjectPom, parentPsi);
    assertSame(parentPsi, resolved);
  }
}
