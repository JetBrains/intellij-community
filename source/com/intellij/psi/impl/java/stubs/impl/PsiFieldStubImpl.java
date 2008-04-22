/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiField;
import com.intellij.psi.impl.cache.InitializerTooLongException;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
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
  private final static int DEPRECATED_ANNOTATION = 0x04;

  public PsiFieldStubImpl(final StubElement parent, final String name, final TypeInfo type, final String initializer, final byte flags) {
    super(parent, isEnumConst(flags) ? JavaStubElementTypes.ENUM_CONSTANT : JavaStubElementTypes.FIELD);

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
    return isEnumConst(myFlags);
  }

  private static boolean isEnumConst(final byte flags) {
    return (flags & ENUM_CONST) != 0;
  }

  public boolean isDeprecated() {
    return (myFlags & DEPRECATED) != 0;
  }

  public boolean hasDeprecatedAnnotation() {
    return (myFlags & DEPRECATED_ANNOTATION) != 0;
  }

  public String getName() {
    return myName;
  }

  public static byte packFlags(boolean isEnumConst, boolean isDeprecated, boolean hasDeprecatedAnnotation) {
    byte flags = 0;
    if (isEnumConst) flags |= ENUM_CONST;
    if (isDeprecated) flags |= DEPRECATED;
    if (hasDeprecatedAnnotation) flags |= DEPRECATED_ANNOTATION;
    return flags;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiFieldStub[");

    if (isDeprecated() || hasDeprecatedAnnotation()) {
      builder.append("deprecated ");
    }

    if (isEnumConstant()) {
      builder.append("enumconst ");
    }

    builder.append(getName()).append(':').append(RecordUtil.createTypeText(getType()));

    if (myInitializer != null) {
      builder.append('=').append(myInitializer);
    }

    builder.append("]");
    return builder.toString();
  }
}