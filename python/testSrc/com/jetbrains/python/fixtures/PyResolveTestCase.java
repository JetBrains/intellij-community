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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.*;
import com.intellij.testFramework.TestDataFile;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author yole
 */
public abstract class PyResolveTestCase extends PyTestCase {
  @NonNls protected static final String MARKER = "<ref>";


  protected PsiReference configureByFile(@TestDataFile final String filePath) {
    VirtualFile testDataRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(getTestDataPath()));
    final VirtualFile file = testDataRoot.findFileByRelativePath(filePath);
    assertNotNull(file);

    String fileText;
    try {
      fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(file));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    int offset = fileText.indexOf(MARKER);
    assertTrue(offset >= 0);
    fileText = fileText.substring(0, offset) + fileText.substring(offset + MARKER.length());
    final String finalFileText = fileText;
    myFixture.configureByText(new File(filePath).getName(), finalFileText);
    final PsiReference reference = myFixture.getFile().findReferenceAt(offset);
    return reference;
  }

  protected abstract PsiElement doResolve() throws Exception;

  protected <T extends PsiElement> T assertResolvesTo(final LanguageLevel langLevel, final Class<T> aClass, final String name) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), langLevel);
    try {
      return assertResolvesTo(aClass, name, null);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  protected <T extends PsiElement> T assertResolvesTo(final Class<T> aClass, final String name) {
    return assertResolvesTo(aClass, name, null);
  }

  protected <T extends PsiElement> T assertResolvesTo(final Class<T> aClass,
                                                         final String name,
                                                         String containingFilePath) {
    final PsiElement element;
    try {
      element = doResolve();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    return assertResolveResult(element, aClass, name, containingFilePath);
  }

  public static <T extends PsiElement> T assertResolveResult(PsiElement element,
                                                             Class<T> aClass,
                                                             String name) {
    return assertResolveResult(element, aClass, name, null);
  }

  public static <T extends PsiElement> T assertResolveResult(PsiElement element,
                                                             Class<T> aClass,
                                                             String name,
                                                             @Nullable String containingFilePath) {
    assertInstanceOf(element, aClass);
    assertEquals(name, ((PsiNamedElement) element).getName());
    if (containingFilePath != null) {
      VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
      if (virtualFile.getFileSystem() instanceof TempFileSystem) {
        assertEquals(containingFilePath, virtualFile.getPath());
      }
      else {
        assertEquals(containingFilePath, virtualFile.getName());
      }

    }
    return (T)element;
  }

  public static int findMarkerOffset(final PsiFile psiFile) {
    Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
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

  @NotNull
  public static PsiPolyVariantReference findReferenceByMarker(PsiFile psiFile) {
    int offset = findMarkerOffset(psiFile);
    final PsiPolyVariantReference ref = (PsiPolyVariantReference)psiFile.findReferenceAt(offset);
    assertNotNull("<ref> in test file not found", ref);
    return ref;
  }
}
