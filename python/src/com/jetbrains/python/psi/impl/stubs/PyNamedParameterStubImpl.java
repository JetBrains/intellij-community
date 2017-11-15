// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;
import org.jetbrains.annotations.Nullable;

public class PyNamedParameterStubImpl extends StubBase<PyNamedParameter> implements PyNamedParameterStub {
  private final String myName;
  private final boolean myPositionalContainer;
  private final boolean myKeywordContainer;
  private final String myTypeCommentAnnotation;
  private final String myAnnotation;
  @Nullable
  private final String myDefaultValueText;

  public PyNamedParameterStubImpl(String name,
                                  boolean isPositionalContainer,
                                  boolean isKeywordContainer,
                                  @Nullable String defaultValueText,
                                  @Nullable String typeCommentAnnotation,
                                  @Nullable String annotation,
                                  StubElement parent,
                                  IStubElementType stubElementType) {
    super(parent, stubElementType);
    myName = name;
    myPositionalContainer = isPositionalContainer;
    myKeywordContainer = isKeywordContainer;
    myTypeCommentAnnotation = typeCommentAnnotation;
    myAnnotation = annotation;
    myDefaultValueText = defaultValueText;
  }

  /**
   * @deprecated Use {@link PyNamedParameterStubImpl#PyNamedParameterStubImpl(String, boolean, boolean, String, String, String, StubElement, IStubElementType)} instead.
   * This constructor will be removed in 2018.2.
   */
  @Deprecated
  public PyNamedParameterStubImpl(String name,
                                  boolean isPositionalContainer,
                                  boolean isKeywordContainer,
                                  boolean hasDefaultValue,
                                  @Nullable String typeCommentAnnotation,
                                  StubElement parent,
                                  IStubElementType stubElementType) {
    this(name,
         isPositionalContainer,
         isKeywordContainer,
         hasDefaultValue ? PyNames.ELLIPSIS : null,
         typeCommentAnnotation,
         null,
         parent,
         stubElementType);
  }

  @Override
  public boolean isPositionalContainer() {
    return myPositionalContainer;
  }

  @Override
  public boolean isKeywordContainer() {
    return myKeywordContainer;
  }

  @Nullable
  @Override
  public String getDefaultValueText() {
    return myDefaultValueText;
  }

  @Nullable
  @Override
  public String getTypeComment() {
    return myTypeCommentAnnotation;
  }

  @Nullable
  @Override
  public String getAnnotation() {
    return myAnnotation;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return "PyNamedParameterStub(" + myName + ")";
  }
}