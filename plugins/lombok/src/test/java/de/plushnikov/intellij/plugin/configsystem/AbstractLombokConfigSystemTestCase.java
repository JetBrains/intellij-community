package de.plushnikov.intellij.plugin.configsystem;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

public abstract class AbstractLombokConfigSystemTestCase extends AbstractLombokParsingTestCase {
  public void doTest() throws IOException {
    final String fullFileName = getTestName(true).replace('$', '/') + ".java";
    final String subPath = fullFileName.substring(0, fullFileName.lastIndexOf('/'));
    final String fileName = fullFileName.substring(fullFileName.lastIndexOf('/') + 1);

    myFixture.copyFileToProject(getBasePath() + "/" + subPath + "/lombok.config", "lombok.config");

    doTest(fullFileName, subPath + "/after/" + fileName);
  }

  protected void doTest(final String beforeFileName, final String afterFileName) throws IOException {
    final PsiFile psiDelombokFile = loadToPsiFile(afterFileName);
    final PsiFile psiLombokFile = loadToPsiFile(beforeFileName);

    if (!(psiLombokFile instanceof PsiJavaFile) || !(psiDelombokFile instanceof PsiJavaFile)) {
      fail("The test file type is not supported");
    }

    compareFiles((PsiJavaFile) psiLombokFile, (PsiJavaFile) psiDelombokFile);
  }
}
