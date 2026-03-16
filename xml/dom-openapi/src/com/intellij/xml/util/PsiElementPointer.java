package com.intellij.xml.util;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public interface PsiElementPointer {
  @Nullable
  PsiElement getPsiElement();
}
