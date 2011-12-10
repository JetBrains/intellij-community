package de.plushnikov.intellij.lombok.util;

import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.PsiElementFactoryImpl;

/**
 * Gets all of primitive types as PsiType
 * Attention: IntelliJ 11 returns PsiPrimitiveType instead of PsiType, so we need a facade here.
 * Before build 103.72 is was PsiType and after this build PsiPrimitiveType
 *
 * @author Plushnikov Michail
 */
public class PsiPrimitiveTypeUtil10Impl implements PsiPrimitiveTypeUtil {
  @Override
  public PsiType getBooleanType() {
    return PsiElementFactoryImpl.getPrimitiveType(PsiKeyword.BOOLEAN);
  }

  @Override
  public PsiType getNullType() {
    return PsiElementFactoryImpl.getPrimitiveType(PsiKeyword.NULL);
  }

  @Override
  public PsiType getVoidType() {
    return PsiElementFactoryImpl.getPrimitiveType(PsiKeyword.VOID);
  }

  @Override
  public PsiType getByteType() {
    return PsiElementFactoryImpl.getPrimitiveType(PsiKeyword.BYTE);
  }

  @Override
  public PsiType getCharType() {
    return PsiElementFactoryImpl.getPrimitiveType(PsiKeyword.CHAR);
  }

  @Override
  public PsiType getFloatType() {
    return PsiElementFactoryImpl.getPrimitiveType(PsiKeyword.FLOAT);
  }

  @Override
  public PsiType getDoubleType() {
    return PsiElementFactoryImpl.getPrimitiveType(PsiKeyword.DOUBLE);
  }

  @Override
  public PsiType getShortType() {
    return PsiElementFactoryImpl.getPrimitiveType(PsiKeyword.SHORT);
  }

  @Override
  public PsiType getIntType() {
    return PsiElementFactoryImpl.getPrimitiveType(PsiKeyword.INT);
  }

  @Override
  public PsiType getLongType() {
    return PsiElementFactoryImpl.getPrimitiveType(PsiKeyword.LONG);
  }
}
