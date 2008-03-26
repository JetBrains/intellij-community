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
  private final byte myFlags;

  private final static int ENUM_CONST = 0x01;
  private final static int DEPRECATED = 0x02;

  public PsiFieldStubImpl(final StubElement parent, final IStubElementType elementType,
                          final String name,
                          final TypeInfo type,
                          final String initializer,
                          final byte flags) {
    super(parent, elementType);

    if (initializer != null && initializer.length() > INITIALIZER_LENGTH_LIMIT) {
      myInitializer = INITIALIZER_TOO_LONG;
    }
    else {
      myInitializer = initializer;
    }

    myName = name;
    myType = type;
    myFlags = flags;
  }

  public TypeInfo getType() {
    return myType;
  }

  public String getInitializerText() throws InitializerTooLongException {
    if (INITIALIZER_TOO_LONG.equals(myInitializer)) throw new InitializerTooLongException();
    return myInitializer;
  }

  public byte getFlags() {
    return myFlags;
  }

  public boolean isEnumConstant() {
    return (myFlags & ENUM_CONST) != 0;
  }

  public boolean isDeprecated() {
    return (myFlags & DEPRECATED) != 0;
  }

  public String getName() {
    return myName;
  }

  public static byte packFlags(boolean isEnumConst, boolean isDeprecated) {
    byte flags = 0;
    if (isEnumConst) flags |= ENUM_CONST;
    if (isDeprecated) flags |= DEPRECATED;
    return flags;
  }
}