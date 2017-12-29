// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.numpy.codeInsight;

import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyClass;
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
  public Collection<PyCustomMember> getMembers(PyClassType clazz, PsiElement location, @NotNull TypeEvalContext context) {
    if (location != null) {
      final PyClass pyClass = clazz.getPyClass();
      if (BUNCH.equals(pyClass.getQualifiedName())) {
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
