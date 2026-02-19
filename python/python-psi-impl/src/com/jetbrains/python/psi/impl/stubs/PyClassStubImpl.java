// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.stubs.PyClassStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class PyClassStubImpl extends PyVersionSpecificStubBase<PyClass> implements PyClassStub {

  private final @Nullable String myName;

  private final @NotNull Map<QualifiedName, QualifiedName> mySuperClasses;

  private final @Nullable QualifiedName myMetaClass;

  private final @Nullable List<String> mySlots;

  private final @Nullable List<String> myMatchArgs;

  private final @Nullable String myDocString;

  private final @NotNull List<String> mySuperClassesText;

  private final @Nullable String myDeprecationMessage;

  private final @Nullable PyCustomClassStub myCustomStub;

  public PyClassStubImpl(@Nullable String name,
                         @Nullable StubElement parentStub,
                         @NotNull Map<QualifiedName, QualifiedName> superClasses,
                         @NotNull List<String> superClassesText,
                         @Nullable QualifiedName metaClass,
                         @Nullable List<String> slots,
                         @Nullable List<String> matchArgs,
                         @Nullable String docString,
                         @Nullable String deprecationMessage,
                         @NotNull IStubElementType stubElementType,
                         @NotNull RangeSet<Version> versions,
                         @Nullable PyCustomClassStub customStub) {
    super(parentStub, stubElementType, versions);
    myName = name;
    mySuperClasses = superClasses;
    mySuperClassesText = superClassesText;
    myMetaClass = metaClass;
    mySlots = slots;
    myMatchArgs = matchArgs;
    myDocString = docString;
    myDeprecationMessage = deprecationMessage;
    myCustomStub = customStub;
  }

  @Override
  public @Nullable String getName() {
    return myName;
  }

  @Override
  public @NotNull Map<QualifiedName, QualifiedName> getSuperClasses() {
    return mySuperClasses;
  }

  @Override
  public @Nullable QualifiedName getMetaClass() {
    return myMetaClass;
  }

  @Override
  public @Nullable List<String> getSlots() {
    return mySlots;
  }

  @Override
  public @Nullable List<String> getMatchArgs() {
    return myMatchArgs;
  }

  @Override
  public @Nullable String getDocString() {
    return myDocString;
  }

  @Override
  public @Nullable String getDeprecationMessage() {
    return myDeprecationMessage;
  }

  @Override
  public @NotNull List<String> getSuperClassesText() {
    return mySuperClassesText;
  }

  @Override
  public @Nullable <T> T getCustomStub(@NotNull Class<T> stubClass) {
    return ObjectUtils.tryCast(myCustomStub, stubClass);
  }

  @Override
  public String toString() {
    // @formatter:off
    return "PyClassStubImpl{" +
           "myName='" + myName + '\'' +
           ", mySuperClasses=" + mySuperClasses +
           ", myMetaClass=" + myMetaClass +
           ", mySlots=" + mySlots +
           ", myMatchArgs=" + myMatchArgs +
           ", myDocString='" + (myDocString != null ? StringUtil.escapeStringCharacters(myDocString) : null) + '\'' +
           ", mySuperClassesText=" + mySuperClassesText +
           ", myDeprecationMessage='" + (myDeprecationMessage != null ? StringUtil.escapeStringCharacters((myDeprecationMessage)) : null) + '\'' +
           ", myCustomStub=" + myCustomStub +
           '}';
    // @formatter:on
  }
}