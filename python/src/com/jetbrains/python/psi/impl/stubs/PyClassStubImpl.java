/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.jetbrains.python.psi.PyClass;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyClassStub;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author max
 */
public class PyClassStubImpl extends StubBase<PyClass> implements PyClassStub {
  private final String myName;
  private final QualifiedName[] mySuperClasses;
  @Nullable private final QualifiedName myMetaClass;
  private final List<String> mySlots;
  private final String myDocString;

  public PyClassStubImpl(final String name, StubElement parentStub, final QualifiedName[] superClasses, @Nullable QualifiedName metaClass,
                         final List<String> slots, String docString, IStubElementType stubElementType) {
    super(parentStub, stubElementType);
    myName = name;
    mySuperClasses = superClasses;
    myMetaClass = metaClass;
    mySlots = slots;
    myDocString = docString;
  }

  public String getName() {
    return myName;
  }

  public QualifiedName[] getSuperClasses() {
    return mySuperClasses;
  }

  @Nullable
  @Override
  public QualifiedName getMetaClass() {
    return myMetaClass;
  }

  @Override
  public List<String> getSlots() {
    return mySlots;
  }

  @Override
  public String getDocString() {
    return myDocString;
  }

  @Override
  public String toString() {
    return "PyClassStub(" + myName + ")";
  }
}