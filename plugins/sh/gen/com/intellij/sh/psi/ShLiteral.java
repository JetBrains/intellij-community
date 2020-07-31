// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.model.psi.UrlReferenceHost;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReference;

public interface ShLiteral extends ShSimpleCommandElement, UrlReferenceHost, PsiNameIdentifierOwner {

  @Nullable
  PsiElement getWord();

  PsiReference[] getReferences();

}
