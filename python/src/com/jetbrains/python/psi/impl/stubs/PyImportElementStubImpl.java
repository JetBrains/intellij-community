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
import com.jetbrains.python.psi.PyImportElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyImportElementStubImpl extends StubBase<PyImportElement> implements PyImportElementStub {
  private final QualifiedName myImportedQName;
  private final String myAsName;

  public PyImportElementStubImpl(@Nullable QualifiedName importedQName, String asName, final StubElement parent,
                                 IStubElementType elementType) {
    super(parent, elementType);
    myImportedQName = importedQName;
    myAsName = asName;
  }

  @Nullable
  public QualifiedName getImportedQName() {
    return myImportedQName;
  }

  public String getAsName() {
    return myAsName;
  }

  @Override
  public String toString() {
    return "PyImportElementStub(importedQName=" + myImportedQName + " asName=" + myAsName + ")";
  }
}
