package org.intellij.plugins.markdown;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;

import java.io.File;
import java.io.IOException;

public class MarkdownFileTypeTest extends HeavyPlatformTestCase {
  public void testMarkdownExtension() throws IOException {
    doTest(".markdown");
  }

  public void testMdExtension() throws IOException {
    doTest(".md");
  }

  private void doTest(String extension) throws IOException {
    File dir = createTempDirectory();
    File file = FileUtil.createTempFile(dir, "test", extension, true);
    VirtualFile virtualFile = getVirtualFile(file);
    assertNotNull(virtualFile);
    PsiFile psi = getPsiManager().findFile(virtualFile);
    assertTrue(psi instanceof MarkdownFile);
    assertEquals(MarkdownFileType.INSTANCE, virtualFile.getFileType());
  }
}
