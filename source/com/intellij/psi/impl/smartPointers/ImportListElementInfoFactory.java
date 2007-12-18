package com.intellij.psi.impl.smartPointers;

import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiImportList;
import com.intellij.openapi.editor.Document;

public class ImportListElementInfoFactory implements SmartPointerElementInfoFactory {
  @Nullable
  public SmartPointerElementInfo createElementInfo(final PsiElement element) {
    if (element instanceof PsiImportList) {
      return new ImportListInfo((PsiJavaFile)element.getContainingFile());
    }
    return null;
  }

  private static class ImportListInfo implements SmartPointerElementInfo {
    private final PsiJavaFile myFile;

    public ImportListInfo(PsiJavaFile file) {
      myFile = file;
    }

    public PsiElement restoreElement() {
      if (!myFile.isValid()) return null;
      return myFile.getImportList();
    }

    public Document getDocumentToSynchronize() {
      return null;
    }

    public void documentAndPsiInSync() {
    }
  }
}
