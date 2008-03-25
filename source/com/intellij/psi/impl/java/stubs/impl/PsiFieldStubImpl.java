/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiField;
import com.intellij.psi.impl.cache.InitializerTooLongException;
import com.intellij.psi.impl.cache.impl.repositoryCache.TypeInfo;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NonNls;

public class PsiFieldStubImpl extends StubBase<PsiField> implements PsiFieldStub {
  private static final int INITIALIZER_LENGTH_LIMIT = 1000;
  public static final @NonNls String INITIALIZER_TOO_LONG = ";INITIALIZER_TOO_LONG;";

  private final String myName;
  private final TypeInfo myType;
  private final String myInitializer;
  private final boolean isEnumConst;

  public PsiFieldStubImpl(final StubElement parent, final IStubElementType elementType,
                          final String name,
                          final TypeInfo type,
                          final String initializer,
                          final boolean enumConst) {
    super(parent, elementType);

    if (initializer != null && initializer.length() > INITIALIZER_LENGTH_LIMIT) {
      myInitializer = INITIALIZER_TOO_LONG;
    }
    else {
      myInitializer = initializer;
    }

    myName = name;
    myType = type;
    isEnumConst = enumConst;
  }

  public TypeInfo getType() {
    return myType;
  }

  public String getInitializerText() throws InitializerTooLongException {
    if (INITIALIZER_TOO_LONG.equals(myInitializer)) throw new InitializerTooLongException();
    return myInitializer;
  }

  public boolean isEnumConstant() {
    return isEnumConst;
  }

  public String getName() {
    return myName;
  }
}