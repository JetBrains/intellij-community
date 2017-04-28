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
package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyElementImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class PyNamedTupleType extends PyClassTypeImpl implements PyCallableType {

  @NotNull
  private final PsiElement myDeclaration;

  @NotNull
  private final String myName;

  @NotNull
  private final List<String> myFields;

  @NotNull
  private final DefinitionLevel myDefinitionLevel;

  public PyNamedTupleType(@NotNull PyClass tupleClass,
                          @NotNull PsiElement declaration,
                          @NotNull String name,
                          @NotNull List<String> fields,
                          @NotNull DefinitionLevel definitionLevel) {
    super(tupleClass, definitionLevel != DefinitionLevel.INSTANCE);
    myDeclaration = declaration;
    myFields = fields;
    myName = name;
    myDefinitionLevel = definitionLevel;
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext,
                                                          boolean inherited) {
    final List<? extends RatedResolveResult> classMembers = super.resolveMember(name, location, direction, resolveContext, inherited);
    if (classMembers != null && !classMembers.isEmpty()) {
      return classMembers;
    }
    if (myFields.contains(name)) {
      // It's important to make a copy of declaration otherwise members will have the same type as their class
      return Collections.singletonList(new RatedResolveResult(RatedResolveResult.RATE_HIGH, new PyElementImpl(myDeclaration.getNode())));
    }
    return null;
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    final List<Object> result = new ArrayList<>();
    Collections.addAll(result, super.getCompletionVariants(completionPrefix, location, context));
    for (String field : myFields) {
      result.add(LookupElementBuilder.create(field));
    }
    return ArrayUtil.toObjectArray(result);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    if (myDefinitionLevel == DefinitionLevel.AS_SUPERCLASS) {
      return new PyNamedTupleType(myClass, myDeclaration, myName, myFields, DefinitionLevel.NEW_TYPE);
    }
    else if (myDefinitionLevel == DefinitionLevel.NEW_TYPE) {
      return new PyNamedTupleType(myClass, myDeclaration, myName, myFields, DefinitionLevel.INSTANCE);
    }

    return null;
  }

  @NotNull
  @Override
  public PyClassType toInstance() {
    return myDefinitionLevel == DefinitionLevel.NEW_TYPE
           ? new PyNamedTupleType(myClass, myDeclaration, myName, myFields, DefinitionLevel.INSTANCE)
           : this;
  }

  @NotNull
  @Override
  public PyClassLikeType toClass() {
    return myDefinitionLevel == DefinitionLevel.INSTANCE
           ? this
           : new PyNamedTupleType(myClass, myDeclaration, myName, myFields, DefinitionLevel.NEW_TYPE);
  }

  @Override
  public String toString() {
    return "PyNamedTupleType: " + myName;
  }

  @NotNull
  @Override
  public Set<String> getMemberNames(boolean inherited, @NotNull TypeEvalContext context) {
    final Set<String> result = super.getMemberNames(inherited, context);
    result.addAll(myFields);

    return result;
  }

  public int getElementCount() {
    return myFields.size();
  }

  @NotNull
  public List<String> getElementNames() {
    return Collections.unmodifiableList(myFields);
  }

  @Override
  public boolean isCallable() {
    return myDefinitionLevel == DefinitionLevel.NEW_TYPE;
  }

  @Nullable
  @Override
  public List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return isCallable() ? ContainerUtil.map(myFields, field -> new PyCallableParameterImpl(field, null)) : null;
  }

  public enum DefinitionLevel {

    AS_SUPERCLASS,
    NEW_TYPE,
    INSTANCE
  }
}
