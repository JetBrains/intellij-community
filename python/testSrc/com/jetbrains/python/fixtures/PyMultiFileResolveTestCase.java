package com.jetbrains.python.fixtures;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonTestUtil;
import junit.framework.Assert;

/**
 * @author yole
 */
public abstract class PyMultiFileResolveTestCase extends PyResolveTestCase {
  protected String myTestFileName;

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/resolve/multiFile/";
  }

  protected PsiElement doResolve(PsiFile psiFile) {
    final PsiPolyVariantReference ref = PyResolveTestCase.findReferenceByMarker(psiFile);
    final PsiManagerImpl psiManager = (PsiManagerImpl)myFixture.getPsiManager();
    psiManager.setAssertOnFileLoadingFilter(new VirtualFileFilter() {
      @Override
      public boolean accept(VirtualFile file) {
        FileType fileType = file.getFileType();
        return fileType == PythonFileType.INSTANCE;
      }
    });
    try {
      final ResolveResult[] resolveResults = ref.multiResolve(false);
      if (resolveResults.length == 0) {
        return null;
      }
      return resolveResults[0].isValidResult() ? resolveResults[0].getElement() : null;
    }
    finally {
      psiManager.setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);
    }
  }


  protected void prepareTestDirectory() {
    final String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
  }

  private PsiFile prepareFile() {
    prepareTestDirectory();
    VirtualFile sourceFile = null;
    for (String ext : new String[]{".py", ".pyx"}) {
      final String fileName = myTestFileName != null ? myTestFileName : getTestName(false) + ext;
      sourceFile = myFixture.findFileInTempDir(fileName);
      if (sourceFile != null) {
        break;
      }
    }
    Assert.assertNotNull("Could not find test file", sourceFile);
    return myFixture.getPsiManager().findFile(sourceFile);
  }

  @Override
  protected PsiElement doResolve() {
    return doResolve(prepareFile());
  }

  protected ResolveResult[] doMultiResolve() {
    PsiFile psiFile = prepareFile();
    final PsiPolyVariantReference ref = PyResolveTestCase.findReferenceByMarker(psiFile);
    return ref.multiResolve(false);
  }
}
