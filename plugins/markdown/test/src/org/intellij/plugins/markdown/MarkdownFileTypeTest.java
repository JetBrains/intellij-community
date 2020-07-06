// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.jetbrains.annotations.NotNull;

public class MarkdownFileTypeTest extends HeavyPlatformTestCase {
  public void testMarkdownExtension() {
    doTest(".markdown");
  }

  public void testMdExtension() {
    doTest(".md");
  }

  private void doTest(@NotNull String extension) {
    VirtualFile virtualFile = getTempDir().createVirtualFile(extension);
    PsiFile psi = getPsiManager().findFile(virtualFile);
    assertTrue(psi instanceof MarkdownFile);
    assertEquals(MarkdownFileType.INSTANCE, virtualFile.getFileType());
  }
}
