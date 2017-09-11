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
package com.jetbrains.numpy.codeInsight;

import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyClassMembersProviderBase;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 *
 * User : ktisha
 */
public class NumpyClassMembersProvider extends PyClassMembersProviderBase {
  private static final String BUNCH = "sklearn.datasets.base.Bunch";
  public static final List<String> BUNCH_MEMBERS = Lists.newArrayList("target", "data", "filenames", "target_names", "DESCR");
  @NotNull
  @Override
  public Collection<PyCustomMember> getMembers(PyClassType clazz, PsiElement location, TypeEvalContext typeEvalContext) {
    if (location != null) {
      final PyClass pyClass = clazz.getPyClass();
      if (pyClass.isSubclass(PyNames.TYPES_FUNCTION_TYPE, typeEvalContext)) {
        final PsiElement element = location.getOriginalElement();
        final PsiReference reference = element.getReference();
        if (reference != null) {
          final PsiElement resolved = reference.resolve();
          if (resolved instanceof PyFunction) {
            final List<PyCustomMember> result = new ArrayList<>();
            if (NumpyUfuncs.isUFunc(((PyFunction)resolved).getName()) && NumpyDocStringTypeProvider.isInsideNumPy(resolved)) {
              for (String method : NumpyUfuncs.UFUNC_METHODS) {
                result.add(new PyCustomMember(method, resolved));
              }
              return result;
            }
          }
        }
      }
      else if (BUNCH.equals(pyClass.getQualifiedName())) {
        final List<PyCustomMember> result = new ArrayList<>();
        for (String member : BUNCH_MEMBERS) {
          result.add(new PyCustomMember(member, NumpyDocStringTypeProvider.NDARRAY, true));
        }
        return result;
      }
    }
    return Collections.emptyList();
  }

}
