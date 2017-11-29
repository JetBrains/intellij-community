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

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyTargetExpression;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyTargetExpressionStubImpl extends StubBase<PyTargetExpression> implements PyTargetExpressionStub {
  private final String myName;
  private final InitializerType myInitializerType;
  private final QualifiedName myInitializer;
  private final boolean myQualified;
  private final String myTypeComment;
  private final String myAnnotation;
  
  @Nullable private final String myDocString;
  private final CustomTargetExpressionStub myCustomStub;

  public PyTargetExpressionStubImpl(String name,
                                    @Nullable String docString,
                                    @Nullable String typeComment,
                                    @Nullable String annotation, 
                                    CustomTargetExpressionStub customStub,
                                    StubElement parent) {
    super(parent, PyElementTypes.TARGET_EXPRESSION);
    myName = name;
    myTypeComment = typeComment;
    myAnnotation = annotation;
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
                                    String annotation,
                                    final StubElement parentStub) {
    super(parentStub, PyElementTypes.TARGET_EXPRESSION);
    myName = name;
    myTypeComment = typeComment;
    myAnnotation = annotation;
    assert initializerType != InitializerType.Custom;
    myInitializerType = initializerType;
    myInitializer = initializer;
    myQualified = qualified;
    myCustomStub = null;
    myDocString = docString;
  }

  public String getName() {
    return myName;
  }

  public InitializerType getInitializerType() {
    return myInitializerType;
  }

  public QualifiedName getInitializer() {
    return myInitializer;
  }

  @Override
  public boolean isQualified() {
    return myQualified;
  }

  @Nullable
  @Override
  public <T extends CustomTargetExpressionStub> T getCustomStub(Class<T> stubClass) {
    if (stubClass.isInstance(myCustomStub)) {
      return stubClass.cast(myCustomStub);
    }
    return null;
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
  public String toString() {
    return "PyTargetExpressionStub(name=" + myName + ")";
  }
}
