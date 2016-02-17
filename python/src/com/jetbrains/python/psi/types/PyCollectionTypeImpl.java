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
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class PyCollectionTypeImpl extends PyClassTypeImpl implements PyCollectionType {
  @NotNull private final List<PyType> myElementTypes;

  public PyCollectionTypeImpl(@NotNull PyClass source, boolean isDefinition, @NotNull List<PyType> elementTypes) {
    super(source, isDefinition);
    myElementTypes = elementTypes;
  }


  @Nullable
  @Override
  public PyType getReturnType(@NotNull final TypeEvalContext context) {
    if (isDefinition()) {
      return new PyCollectionTypeImpl(getPyClass(), false, myElementTypes);
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull final TypeEvalContext context, @Nullable final PyCallSiteExpression callSite) {
    return getReturnType(context);
  }

  @NotNull
  @Override
  public List<PyType> getElementTypes(@NotNull TypeEvalContext context) {
    return myElementTypes;
  }

  @Nullable
  public static PyCollectionTypeImpl createTypeByQName(@NotNull final PsiElement anchor,
                                                       @NotNull final String classQualifiedName,
                                                       final boolean isDefinition,
                                                       @NotNull final List<PyType> elementTypes) {
    final PyClass pyClass = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(classQualifiedName, anchor);
    if (pyClass == null) {
      return null;
    }
    return new PyCollectionTypeImpl(pyClass, isDefinition, elementTypes);
  }

  @Override
  public PyClassType toInstance() {
    return myIsDefinition ? new PyCollectionTypeImpl(myClass, false, myElementTypes) : this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PyCollectionType)) return false;
    if (!super.equals(o)) return false;

    PyCollectionType type = (PyCollectionType)o;

    final TypeEvalContext context = TypeEvalContext.codeInsightFallback(myClass.getProject());
    final List<PyType> otherElementTypes = type.getElementTypes(context);
    if (myElementTypes.size() != otherElementTypes.size()) return false;
    for (int i = 0; i < myElementTypes.size(); i++) {
      final PyType elementType = myElementTypes.get(i);
      final PyType otherElementType = otherElementTypes.get(i);
      if (elementType == null && otherElementType != null) return false;
      if (elementType != null && !elementType.equals(otherElementType)) return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result;
    for (PyType type : myElementTypes) {
      result += type != null ? type.hashCode() : 0;
    }
    return result;
  }
}