package de.plushnikov.intellij.lombok.util;

import com.intellij.psi.PsiType;

/**
 * Gets all of primitive types as PsiType
 * Attention: IntelliJ 11 returns PsiPrimitiveType instead of PsiType, so we need a facade here.
 * @author Plushnikov Michail
 */
public class PsiPrimitiveTypeUtil11Impl implements PsiPrimitiveTypeUtil {
  @Override
  public PsiType getBooleanType() {
    return PsiType.BOOLEAN;
  }

  @Override
  public PsiType getNullType() {
    return PsiType.NULL;
  }

  @Override
  public PsiType getVoidType() {
    return PsiType.VOID;
  }

  @Override
  public PsiType getByteType() {
    return PsiType.BYTE;
  }

  @Override
  public PsiType getCharType() {
    return PsiType.CHAR;
  }

  @Override
  public PsiType getFloatType() {
    return PsiType.FLOAT;
  }

  @Override
  public PsiType getDoubleType() {
    return PsiType.DOUBLE;
  }

  @Override
  public PsiType getShortType() {
    return PsiType.SHORT;
  }

  @Override
  public PsiType getIntType() {
    return PsiType.INT;
  }

  @Override
  public PsiType getLongType() {
    return PsiType.LONG;
  }
}
