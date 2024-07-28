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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.stubs.PyVersionRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyTargetExpressionStubImpl extends PyVersionSpecificStubBase<PyTargetExpression> implements PyTargetExpressionStub {
  private final String myName;
  private final InitializerType myInitializerType;
  private final QualifiedName myInitializer;
  private final boolean myQualified;
  private final String myTypeComment;
  private final String myAnnotation;
  private final boolean myHasAssignedValue;
  
  @Nullable private final String myDocString;
  private final CustomTargetExpressionStub myCustomStub;

  public PyTargetExpressionStubImpl(String name,
                                    @Nullable String docString,
                                    @Nullable String typeComment,
                                    @Nullable String annotation,
                                    boolean hasAssignedValue,
                                    CustomTargetExpressionStub customStub,
                                    StubElement parent,
                                    @NotNull PyVersionRange versionRange) {
    super(parent, PyStubElementTypes.TARGET_EXPRESSION, versionRange);
    myName = name;
    myTypeComment = typeComment;
    myAnnotation = annotation;
    myHasAssignedValue = hasAssignedValue;
    myInitializerType = InitializerType.Custom;
    myInitializer = null;
    myQualified = false;
    myCustomStub = customStub;
    myDocString = docString;
  }
  
  public PyTargetExpressionStubImpl(final String name,
                                    @Nullable String docString,
                                    final InitializerType initializerType,
                                    final QualifiedName initializer,
                                    final boolean qualified,
                                    @Nullable String typeComment,
                                    @Nullable String annotation,
                                    boolean hasAssignedValue,
                                    final StubElement parentStub,
                                    @NotNull PyVersionRange versionRange) {
    super(parentStub, PyStubElementTypes.TARGET_EXPRESSION, versionRange);
    myName = name;
    myTypeComment = typeComment;
    myAnnotation = annotation;
    myHasAssignedValue = hasAssignedValue;
    assert initializerType != InitializerType.Custom;
    myInitializerType = initializerType;
    myInitializer = initializer;
    myQualified = qualified;
    myCustomStub = null;
    myDocString = docString;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public InitializerType getInitializerType() {
    return myInitializerType;
  }

  @Override
  public QualifiedName getInitializer() {
    return myInitializer;
  }

  @Override
  public boolean isQualified() {
    return myQualified;
  }

  @Nullable
  @Override
  public <T> T getCustomStub(Class<T> stubClass) {
    return ObjectUtils.tryCast(myCustomStub, stubClass);
  }

  @Nullable
  @Override
  public String getDocString() {
    return myDocString;
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
  public boolean hasAssignedValue() {
    return myHasAssignedValue;
  }

  @Override
  public String toString() {
    String result = "PyTargetExpressionStub(name=" + myName +
                    ", hasAssignedValue=" + myHasAssignedValue;
    if (myCustomStub == null) {
      if (myInitializer != null) {
        result += ", initializer=" + myInitializer;
      }
      result += ", initializerType=" + myInitializerType +
                ", qualified=" + myQualified;
    }
    else {
      result += ", customStub=" + myCustomStub;
    }
    if (myTypeComment != null) {
      result += ", typeComment='" + myTypeComment + '\'';
    }
    if (myAnnotation != null) {
      result += ", annotation='" + myAnnotation + '\'';
    }
    if (myDocString != null) {
      result += ", docString='" + StringUtil.escapeStringCharacters(myDocString) + '\'';
    }
    result += ")";
    return result;
  }
}
