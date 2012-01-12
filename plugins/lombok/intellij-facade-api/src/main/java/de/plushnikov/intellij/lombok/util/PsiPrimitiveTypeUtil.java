package de.plushnikov.intellij.lombok.util;

import com.intellij.psi.PsiType;

/**
 * Gets all of primitive types as PsiType
 * Attention: IntelliJ 11 returns PsiPrimitiveType instead of PsiType, so we need a facade here.
 * @author Plushnikov Michail
 */
public interface PsiPrimitiveTypeUtil {
  PsiType getNullType();

  PsiType getVoidType();

  PsiType getBooleanType();

  PsiType getByteType();

  PsiType getCharType();

  PsiType getFloatType();

  PsiType getDoubleType();

  PsiType getShortType();

  PsiType getIntType();

  PsiType getLongType();
}
