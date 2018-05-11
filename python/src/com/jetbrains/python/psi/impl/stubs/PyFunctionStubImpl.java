// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  private final boolean myGenerator;
  private final boolean myOnlyRaisesNotImplementedError;
  private final String myTypeComment;
  private final String myAnnotation;

  public PyFunctionStubImpl(@Nullable String name,
                            @Nullable String docString,
                            @Nullable String deprecationMessage,
                            boolean isAsync,
                            boolean isGenerator,
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
    myGenerator = isGenerator;
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
  public boolean isGenerator() {
    return myGenerator;
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