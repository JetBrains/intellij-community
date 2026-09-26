// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyFunctionStubImpl extends PyVersionSpecificStubBase<PyFunction> implements PyFunctionStub {
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
                            @NotNull IStubElementType stubElementType,
                            @NotNull RangeSet<Version> versions) {
    super(parent, stubElementType, versions);
    myName = name;
    myDocString = docString;
    myDeprecationMessage = deprecationMessage;
    myAsync = isAsync;
    myGenerator = isGenerator;
    myOnlyRaisesNotImplementedError = onlyRaisesNotImplementedError;
    myTypeComment = typeCommentContent;
    myAnnotation = annotation;
  }

  @Override
  public @Nullable String getName() {
    return myName;
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
  public @Nullable String getTypeComment() {
    return myTypeComment;
  }

  @Override
  public @Nullable String getAnnotation() {
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
    // @formatter:off
    return "PyFunctionStubImpl{" +
           "myName='" + myName + '\'' +
           ", myDocString='" + (myDocString != null ? StringUtil.escapeStringCharacters(myDocString) : null) + '\'' +
           ", myDeprecationMessage='" + (myDeprecationMessage != null ? StringUtil.escapeStringCharacters(myDeprecationMessage) : null) + '\'' +
           ", myAsync=" + myAsync +
           ", myGenerator=" + myGenerator +
           ", myOnlyRaisesNotImplementedError=" + myOnlyRaisesNotImplementedError +
           ", myTypeComment='" + myTypeComment + '\'' +
           ", myAnnotation='" + myAnnotation + '\'' +
           '}';
    // @formatter:on
  }
}