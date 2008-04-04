/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.cache.impl.repositoryCache.RecordUtil;
import com.intellij.psi.impl.cache.impl.repositoryCache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.java.stubs.PsiParameterListStub;
import com.intellij.psi.impl.java.stubs.PsiParameterStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

import java.util.List;

public class PsiMethodStubImpl extends StubBase<PsiMethod> implements PsiMethodStub {
  private TypeInfo myReturnType;
  private final byte myFlags;
  private final String myName;
  private String myDefaultValueText;

  private final static int CONSTRUCTOR = 0x01;
  private final static int VARARGS = 0x02;
  private final static int ANNOTATION = 0x04;
  private final static int DEPRECATED = 0x08;


  public PsiMethodStubImpl(final StubElement parent,
                           final String name,
                           final TypeInfo returnType,
                           final byte flags,
                           final String defaultValueText) {
    super(parent, JavaStubElementTypes.METHOD);

    myReturnType = returnType;
    myFlags = flags;
    myName = name;
    myDefaultValueText = defaultValueText;
  }

  public boolean isConstructor() {
    return (myFlags & CONSTRUCTOR) != 0;
  }

  public boolean isVarArgs() {
    return (myFlags & VARARGS) != 0;
  }

  public boolean isAnnotationMethod() {
    return isAnnotationMethod(myFlags);
  }

  public static boolean isAnnotationMethod(final byte flags) {
    return (flags & ANNOTATION) != 0;
  }

  public String getDefaultValueText() {
    return myDefaultValueText;
  }

  public TypeInfo getReturnTypeText() {
    return myReturnType;
  }

  public boolean isDeprecated() {
    return (myFlags & DEPRECATED) != 0;
  }

  public PsiParameterStub findParameter(final int idx) {
    PsiParameterListStub list = null;
    for (StubElement child : getChildrenStubs()) {
      if (child instanceof PsiParameterListStub) {
        list = (PsiParameterListStub)child;
        break;
      }
    }

    if (list != null) {
      final List<StubElement> params = list.getChildrenStubs();
      return (PsiParameterStub)params.get(idx);
    }

    throw new RuntimeException("No parameter(s) [yet?]");
  }

  public String getName() {
    return myName;
  }

  public byte getFlags() {
    return myFlags;
  }

  public void setDefaultValueText(final String defaultValueText) {
    myDefaultValueText = defaultValueText;
  }

  public void setReturnType(final TypeInfo returnType) {
    myReturnType = returnType;
  }

  public static byte packFlags(boolean isConstructor, boolean isAnnotationMethod, boolean isVarargs, boolean isDeprecated) {
    byte flags = 0;
    if (isConstructor) flags |= CONSTRUCTOR;
    if (isAnnotationMethod) flags |= ANNOTATION;
    if (isVarargs) flags |= VARARGS;
    if (isDeprecated) flags |= DEPRECATED;
    return flags;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiMethodStub[");
    if (isConstructor()) {
      builder.append("cons ");
    }
    if (isAnnotationMethod()) {
      builder.append("annotation ");
    }
    if (isVarArgs()) {
      builder.append("varargs ");
    }
    if (isDeprecated()) {
      builder.append("deprecated ");
    }

    builder.append(getName()).
        append(":").append(RecordUtil.createTypeText(getReturnTypeText()));

    if (getDefaultValueText() != null) {
      builder.append(" default=").append(getDefaultValueText());
    }

    builder.append("]");
    return builder.toString();
  }
}