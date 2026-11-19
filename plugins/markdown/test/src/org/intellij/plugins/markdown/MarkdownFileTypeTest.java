// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class MarkdownFileTypeTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  @TempDir
  Path tempDir;

  @Test
  public void testMarkdownExtension() throws IOException {
    doTest(".markdown");
  }

  @Test
  public void testMdExtension() throws IOException {
    doTest(".md");
  }

  @Test
  public void testMdcExtension() throws IOException {
    doTest(".mdc");
  }

  private void doTest(@NotNull String extension) throws IOException {
    Path path = Files.createFile(tempDir.resolve("file" + extension));
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
    assertNotNull(virtualFile);
    PsiFile psi = ReadAction.computeBlocking(() -> PsiManager.getInstance(projectFixture.get()).findFile(virtualFile));
    assertInstanceOf(MarkdownFile.class, psi);
    assertEquals(MarkdownFileType.INSTANCE, virtualFile.getFileType());
  }
}
