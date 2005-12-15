package com.intellij.psi.impl.compiled;

import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public interface ClsModifierListOwner extends PsiModifierListOwner {
  @NotNull ClsAnnotationImpl[] getAnnotations();
}
