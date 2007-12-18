package com.intellij.psi.impl.smartPointers;

import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ImplicitVariable;
import com.intellij.psi.PsiIdentifier;
import com.intellij.openapi.editor.Document;

public class ImplicitVariableElementInfoFactory implements SmartPointerElementInfoFactory {
  @Nullable
  public SmartPointerElementInfo createElementInfo(final PsiElement element) {
    if (element instanceof ImplicitVariable) {
      return new ImplicitVariableInfo((ImplicitVariable) element);
    }
    return null;
  }

  private static class ImplicitVariableInfo implements SmartPointerElementInfo {
    private final ImplicitVariable myVar;

    public ImplicitVariableInfo(ImplicitVariable var) {
      myVar = var;
    }

    public PsiElement restoreElement() {
      PsiIdentifier psiIdentifier = myVar.getNameIdentifier();
      if (psiIdentifier == null || psiIdentifier.isValid()) return myVar;
      return null;
    }

    @Nullable
    public Document getDocumentToSynchronize() {
      return null;
    }

    public void documentAndPsiInSync() {
    }
  }
}
