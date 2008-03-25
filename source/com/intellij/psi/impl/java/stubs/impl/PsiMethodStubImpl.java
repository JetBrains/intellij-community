/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.cache.impl.repositoryCache.TypeInfo;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiMethodStubImpl extends StubBase<PsiMethod> implements PsiMethodStub {
  private final TypeInfo myReturnType;
  private final byte myFlags;
  private final String myName;
  private final String myDefaultValueText;

  private final static int CONSTRUCTOR = 0x01;
  private final static int VARARGS = 0x02;
  private final static int ANNOTATION = 0x04;


  public PsiMethodStubImpl(final StubElement parent, final IStubElementType elementType,
                           final String name,
                           final TypeInfo returnType,
                           final byte flags,
                           final String defaultValueText) {
    super(parent, elementType);
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

  public String getName() {
    return myName;
  }

  public byte getFlags() {
    return myFlags;
  }

  public static byte packFlags(boolean isConstructor, boolean isAnnotationMethod, boolean isVarargs) {
    byte flags = 0;
    if (isConstructor) flags |= CONSTRUCTOR;
    if (isAnnotationMethod) flags |= ANNOTATION;
    if (isVarargs) flags |= VARARGS;
    return flags;
  }
}