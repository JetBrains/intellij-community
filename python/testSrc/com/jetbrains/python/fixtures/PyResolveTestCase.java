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

import java.io.File;
import java.io.IOException;

/**
 * @author yole
 */
public abstract class PyResolveTestCase extends PyLightFixtureTestCase {
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

  protected int findMarkerOffset(final PsiFile psiFile) {
    Document document = PsiDocumentManager.getInstance(myFixture.getProject()).getDocument(psiFile);
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
