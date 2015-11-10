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
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonTestUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

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
    final PsiReference ref = PyResolveTestCase.findReferenceByMarker(psiFile);
    final PsiManagerImpl psiManager = (PsiManagerImpl)myFixture.getPsiManager();
    psiManager.setAssertOnFileLoadingFilter(new VirtualFileFilter() {
      @Override
      public boolean accept(VirtualFile file) {
        FileType fileType = file.getFileType();
        return fileType == PythonFileType.INSTANCE;
      }
    }, myTestRootDisposable);
    final PsiElement result;
    if (ref instanceof PsiPolyVariantReference) {
      final ResolveResult[] resolveResults = ((PsiPolyVariantReference)ref).multiResolve(false);
      result = resolveResults.length == 0 || !resolveResults[0].isValidResult() ? null : resolveResults[0].getElement();
    }
    else {
      result = ref.resolve();
    }
    psiManager.setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, myTestRootDisposable);
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
      return ContainerUtil.map(((PsiPolyVariantReference)ref).multiResolve(false), new Function<ResolveResult, PsiElement>() {
        @Override
        public PsiElement fun(ResolveResult result) {
          return result.getElement();
        }
      });
    }
    return Collections.singletonList(ref.resolve());
  }
}
