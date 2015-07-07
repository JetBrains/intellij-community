/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    }, myTestRootDisposable);
    final ResolveResult[] resolveResults = ref.multiResolve(false);
    psiManager.setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, myTestRootDisposable);
    if (resolveResults.length == 0) {
      return null;
    }
    return resolveResults[0].isValidResult() ? resolveResults[0].getElement() : null;
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

  protected ResolveResult[] doMultiResolve() {
    PsiFile psiFile = prepareFile();
    final PsiPolyVariantReference ref = PyResolveTestCase.findReferenceByMarker(psiFile);
    return ref.multiResolve(false);
  }
}
