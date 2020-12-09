package de.plushnikov.intellij.plugin.configsystem;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

public abstract class AbstractLombokConfigSystemTestCase extends AbstractLombokParsingTestCase {
  @Override
  public void doTest() {
    final String fullFileName = getTestName(true).replace('$', '/') + ".java";
    final int lastIndexOf = fullFileName.lastIndexOf('/');
    final String subPath = fullFileName.substring(0, lastIndexOf);
    final String fileName = fullFileName.substring(lastIndexOf + 1);

    myFixture.copyFileToProject(subPath + "/before/lombok.config", subPath + "/before/lombok.config");

    doTest(subPath + "/before/" + fileName, subPath + "/after/" + fileName);
  }

  protected void doTest(final String beforeFileName, final String afterFileName) {
    final PsiFile psiDelombokFile = loadToPsiFile(afterFileName);
    final PsiFile psiLombokFile = loadToPsiFile(beforeFileName);

    if (!(psiLombokFile instanceof PsiJavaFile) || !(psiDelombokFile instanceof PsiJavaFile)) {
      fail("The test file type is not supported");
    }

    compareFiles((PsiJavaFile) psiLombokFile, (PsiJavaFile) psiDelombokFile);
  }
}
