package com.jetbrains.python;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.PsiTestUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;

/**
 * @author yole
 */
public class PyMultiFileResolveTest extends CodeInsightTestCase {
  public void testSimple() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyFile);
    assertEquals("ImportedFile.py", ((PyFile) element).getName());
  }

  public void testFromImport() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyFunction);
    assertEquals("func", ((PyFunction) element).getName());
  }

  public void testFromImportStar() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyFunction);
    assertEquals("func", ((PyFunction) element).getName());
  }

  private PsiElement doResolve() throws Exception {
    String testName = getTestName(true);
    String fileName = getTestName(false) + ".py";
    String root = PathManager.getHomePath() + "/plugins/python/testData/resolve/multiFile/" + testName;
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete, false);
    PsiTestUtil.addSourceContentToRoots(myModule, rootDir);
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    VirtualFile sourceFile = rootDir.findChild(fileName);
    assert sourceFile != null;
    PsiFile psiFile = myPsiManager.findFile(sourceFile);
    int offset = findMarkerOffset(psiFile);
    final PsiReference ref = psiFile.findReferenceAt(offset);
    return ref.resolve();
  }

  private int findMarkerOffset(final PsiFile psiFile) {
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
    assert document != null;
    int offset = -1;
    for (int i=1; i<document.getLineCount(); i++) {
      int lineStart = document.getLineStartOffset(i);
      int lineEnd = document.getLineEndOffset(i);
      final int index=document.getCharsSequence().subSequence(lineStart, lineEnd).toString().indexOf("<ref>");
      if (index>0) {
        offset = document.getLineStartOffset(i-1) + index;
      }
    }
    assert offset != -1;
    return offset;
  }
}
