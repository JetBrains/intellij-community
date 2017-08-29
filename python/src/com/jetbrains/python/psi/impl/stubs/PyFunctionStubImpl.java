/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyFunctionStubImpl extends StubBase<PyFunction> implements PyFunctionStub {
  private final String myName;
  private final String myDocString;
  private final String myDeprecationMessage;
  private final boolean myAsync;
  private final boolean myOnlyRaisesNotImplementedError;
  private final String myTypeComment;
  private final String myAnnotation;

  public PyFunctionStubImpl(@Nullable String name,
                            @Nullable String docString,
                            @Nullable String deprecationMessage,
                            boolean isAsync,
                            boolean onlyRaisesNotImplementedError,
                            @Nullable String typeCommentContent,
                            @Nullable String annotation, 
                            final StubElement parent,
                            @NotNull IStubElementType stubElementType) {
    super(parent, stubElementType);
    myName = name;
    myDocString = docString;
    myDeprecationMessage = deprecationMessage;
    myAsync = isAsync;
    myOnlyRaisesNotImplementedError = onlyRaisesNotImplementedError;
    myTypeComment = typeCommentContent;
    myAnnotation = annotation;
  }

  @Nullable
  @Override
  public String getName() {
    return myName;
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

  @Nullable
  @Override
  public String getTypeComment() {
    return myTypeComment;
  }

  @Nullable
  @Override
  public String getAnnotation() {
    return myAnnotation;
  }

  @Override
  public boolean isAsync() {
    return myAsync;
  }

  @Override
  public boolean onlyRaisesNotImplementedError() {
    return myOnlyRaisesNotImplementedError;
  }

  @Override
  public String toString() {
    return "PyFunctionStub(" + myName + ")";
  }
}