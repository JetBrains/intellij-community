// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyVersionRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class PyClassStubImpl extends PyVersionSpecificStubBase<PyClass> implements PyClassStub {

  @Nullable
  private final String myName;

  @NotNull
  private final Map<QualifiedName, QualifiedName> mySuperClasses;

  @Nullable
  private final QualifiedName myMetaClass;

  @Nullable
  private final List<String> mySlots;

  @Nullable
  private final String myDocString;

  @NotNull
  private final List<String> mySuperClassesText;

  @Nullable
  private final String myDeprecationMessage;

  @Nullable
  private final PyCustomClassStub myCustomStub;

  public PyClassStubImpl(@Nullable String name,
                         @Nullable StubElement parentStub,
                         @NotNull Map<QualifiedName, QualifiedName> superClasses,
                         @NotNull List<String> superClassesText,
                         @Nullable QualifiedName metaClass,
                         @Nullable List<String> slots,
                         @Nullable String docString,
                         @Nullable String deprecationMessage,
                         @NotNull IStubElementType stubElementType,
                         @NotNull PyVersionRange versionRange,
                         @Nullable PyCustomClassStub customStub) {
    super(parentStub, stubElementType, versionRange);
    myName = name;
    mySuperClasses = superClasses;
    mySuperClassesText = superClassesText;
    myMetaClass = metaClass;
    mySlots = slots;
    myDocString = docString;
    myDeprecationMessage = deprecationMessage;
    myCustomStub = customStub;
  }

  @Override
  @Nullable
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public Map<QualifiedName, QualifiedName> getSuperClasses() {
    return mySuperClasses;
  }

  @Nullable
  @Override
  public QualifiedName getMetaClass() {
    return myMetaClass;
  }

  @Nullable
  @Override
  public List<String> getSlots() {
    return mySlots;
  }

  @Nullable
  @Override
  public String getDocString() {
    return myDocString;
  }

  @Nullable
  @Override
  public String getDeprecationMessage() {
    return myDeprecationMessage;
  }

  @NotNull
  @Override
  public List<String> getSuperClassesText() {
    return mySuperClassesText;
  }

  @Nullable
  @Override
  public <T> T getCustomStub(@NotNull Class<T> stubClass) {
    return ObjectUtils.tryCast(myCustomStub, stubClass);
  }

  @Override
  public String toString() {
    return "PyClassStub(" + myName + ")";
  }
}