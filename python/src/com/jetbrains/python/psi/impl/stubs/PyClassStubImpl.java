/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.stubs.PyClassStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author max
 */
public class PyClassStubImpl extends StubBase<PyClass> implements PyClassStub {

  @Nullable
  private final String myName;

  @NotNull
  private final Map<QualifiedName, QualifiedName> mySuperClasses;
  private final List<String> mySubscriptedSuperClassesText;

  @Nullable
  private final QualifiedName myMetaClass;

  @Nullable
  private final List<String> mySlots;

  @Nullable
  private final String myDocString;

  @NotNull
  private final List<String> mySuperClassesText;

  public PyClassStubImpl(@Nullable String name,
                         @Nullable StubElement parentStub,
                         @NotNull Map<QualifiedName, QualifiedName> superClasses,
                         @NotNull List<String> subscriptedSuperClassesText,
                         @Nullable QualifiedName metaClass,
                         @Nullable List<String> slots,
                         @Nullable String docString,
                         @NotNull IStubElementType stubElementType) {
    this(name, parentStub, superClasses, subscriptedSuperClassesText, Collections.emptyList(), metaClass, slots, docString, stubElementType);
  }

  public PyClassStubImpl(@Nullable String name,
                         @Nullable StubElement parentStub,
                         @NotNull Map<QualifiedName, QualifiedName> superClasses,
                         @NotNull List<String> subscriptedSuperClassesText,
                         @NotNull List<String> superClassesText,
                         @Nullable QualifiedName metaClass,
                         @Nullable List<String> slots,
                         @Nullable String docString,
                         @NotNull IStubElementType stubElementType) {
    super(parentStub, stubElementType);
    myName = name;
    mySuperClasses = superClasses;
    mySubscriptedSuperClassesText = subscriptedSuperClassesText;
    mySuperClassesText = superClassesText;
    myMetaClass = metaClass;
    mySlots = slots;
    myDocString = docString;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @NotNull
  public Map<QualifiedName, QualifiedName> getSuperClasses() {
    return mySuperClasses;
  }

  @NotNull
  @Override
  public List<String> getSubscriptedSuperClasses() {
    return mySubscriptedSuperClassesText;
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

  @NotNull
  @Override
  public List<String> getSuperClassesText() {
    return mySuperClassesText;
  }

  @Override
  public String toString() {
    return "PyClassStub(" + myName + ")";
  }
}