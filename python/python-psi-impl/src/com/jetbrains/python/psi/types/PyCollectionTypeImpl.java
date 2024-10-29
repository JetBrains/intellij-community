/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class PyCollectionTypeImpl extends PyClassTypeImpl implements PyCollectionType {
  @NotNull protected final List<PyType> myElementTypes;
  protected final Ref<Integer> hashCode = new Ref<>();

  public PyCollectionTypeImpl(@NotNull PyClass source, boolean isDefinition, @NotNull List<? extends PyType> elementTypes) {
    super(source, isDefinition);
    myElementTypes = new ArrayList<>(elementTypes);
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
  public List<PyType> getElementTypes() {
    return Collections.unmodifiableList(myElementTypes);
  }

  @Nullable
  public static PyCollectionTypeImpl createTypeByQName(@NotNull final PsiElement anchor,
                                                       @NotNull final String classQualifiedName,
                                                       final boolean isDefinition,
                                                       @NotNull final List<? extends PyType> elementTypes) {
    final PyClass pyClass = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(classQualifiedName, anchor);
    if (pyClass == null) {
      return null;
    }
    return new PyCollectionTypeImpl(pyClass, isDefinition, elementTypes);
  }

  @NotNull
  @Override
  public PyClassType toInstance() {
    return myIsDefinition ? withUserDataCopy(new PyCollectionTypeImpl(myClass, false, myElementTypes)) : this;
  }

  @NotNull
  @Override
  public PyClassLikeType toClass() {
    return myIsDefinition ? this : withUserDataCopy(new PyCollectionTypeImpl(myClass, true, myElementTypes));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final PyCollectionTypeImpl that = (PyCollectionTypeImpl)o;

    if (!myElementTypes.equals(that.myElementTypes)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    if (hashCode.isNull()) {
      int result = super.hashCode();
      result = 31 * result;
      for (PyType type : myElementTypes) {
        result += type != null ? type.hashCode() : 0;
      }
      hashCode.set(result);
    }

    return hashCode.get();
  }

  @Nullable
  @Override
  public PyType getIteratedItemType() {
    // TODO: Select the parameter type that matches T in Iterable[T]
    return ContainerUtil.getFirstItem(myElementTypes);
  }

  @Override
  public String toString() {
    return ((isValid() ? "" : "[INVALID] ") + "PyCollectionClassType: " + getClassQName()) +
           "[" + StringUtil.join(getElementTypes(), ", ") + "]";
  }
}