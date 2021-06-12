// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.fixtures;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonTestUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;


public abstract class PyMultiFileResolveTestCase extends PyResolveTestCase {
  protected String myTestFileName;

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/resolve/multiFile/";
  }

  protected PsiElement doResolve(PsiFile psiFile) {
    final PsiReference ref = PyResolveTestCase.findReferenceByMarker(psiFile);
    final PsiManagerEx psiManager = (PsiManagerEx)myFixture.getPsiManager();
    psiManager.setAssertOnFileLoadingFilter(file -> {
      FileType fileType = file.getFileType();
      return fileType == PythonFileType.INSTANCE;
    }, myFixture.getTestRootDisposable());
    final PsiElement result;
    if (ref instanceof PsiPolyVariantReference) {
      final ResolveResult[] resolveResults = ((PsiPolyVariantReference)ref).multiResolve(false);
      result = resolveResults.length == 0 || !resolveResults[0].isValidResult() ? null : resolveResults[0].getElement();
    }
    else {
      result = ref.resolve();
    }
    psiManager.setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, myFixture.getTestRootDisposable());
    return result;
  }


  protected void prepareTestDirectory() {
    final String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
  }

  protected PsiFile prepareFile() {
    prepareTestDirectory();
    VirtualFile sourceFile = null;
    for (String ext : new String[]{".py", ".pyx", ".pyi"}) {
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

  @NotNull
  protected List<PsiElement> doMultiResolve() {
    final PsiFile psiFile = prepareFile();
    final PsiReference ref = PyResolveTestCase.findReferenceByMarker(psiFile);
    if (ref instanceof PsiPolyVariantReference) {
      return ContainerUtil.map(((PsiPolyVariantReference)ref).multiResolve(false), ResolveResult::getElement);
    }
    return Collections.singletonList(ref.resolve());
  }

}
