// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.collect.Collections2;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PyMembersUtil {
  /**
   * Filters out {@link PyMemberInfo}
   * that should not be displayed in this refactoring (like object)
   *
   * @param pyMemberInfos collection to sort
   * @return sorted collection
   */
  @NotNull
  public static Collection<PyMemberInfo<PyElement>> filterOutObject(@NotNull final Collection<PyMemberInfo<PyElement>> pyMemberInfos) {
    return Collections2.filter(pyMemberInfos, new ObjectPredicate(false));
  }

  public static class ObjectPredicate extends NotNullPredicate<PyMemberInfo<PyElement>> {
    private final boolean myAllowObjects;

    /**
     * @param allowObjects allows only objects if true. Allows all but objects otherwise.
     */
    public ObjectPredicate(final boolean allowObjects) {
      myAllowObjects = allowObjects;
    }

    @Override
    public boolean applyNotNull(@NotNull final PyMemberInfo<PyElement> input) {
      return myAllowObjects == isObject(input);
    }

    private static boolean isObject(@NotNull final PyMemberInfo<PyElement> classMemberInfo) {
      final PyElement element = classMemberInfo.getMember();
      return (element instanceof PyClass) && PyNames.OBJECT.equals(element.getName());
    }
  }
}
