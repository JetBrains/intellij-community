/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
public class PyMemberInfo<T extends PyElement> extends MemberInfoBase<T> {
  @NotNull
  private final MembersManager<T> myMembersManager;
  private final boolean myCouldBeAbstract;

  /**
   * @param couldBeAbstract if element could be marked as abstract (like abstract method)
   * @param member         element itself
   * @param isStatic       is it static or not?
   * @param displayName    element display name
   * @param overrides      does it overrides something? TRUE if is overriden, FALSE if implemented, null if not implemented or overriden
   *                       TODO: use primitive instead? "Implemeneted" has nothing to do with python duck-typing
   * @param membersManager manager that knows how to handle this member
   */
  PyMemberInfo(@NotNull final T member,
               final boolean isStatic,
               @NotNull final String displayName,
               @Nullable final Boolean overrides,
               @NotNull final MembersManager<T> membersManager,
               final boolean couldBeAbstract) {
    super(member);
    this.isStatic = isStatic;
    this.displayName = displayName;
    this.overrides = overrides;
    myMembersManager = membersManager;
    myCouldBeAbstract = couldBeAbstract;
  }

  @NotNull
  MembersManager<T> getMembersManager() {
    return myMembersManager;
  }

  public boolean isCouldBeAbstract() {
    return myCouldBeAbstract;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof PyMemberInfo) {
      return getMember().equals(((PyMemberInfo<?>)obj).getMember());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return getMember().hashCode();
  }

  /**
   * Checks if moving this member to some class may create conflict.
   * @param destinationClass destination class to check
   * @return true if conflict.
   */
  public boolean hasConflict(@NotNull final PyClass destinationClass) {
    return myMembersManager.hasConflict(getMember(), destinationClass);
  }
}
