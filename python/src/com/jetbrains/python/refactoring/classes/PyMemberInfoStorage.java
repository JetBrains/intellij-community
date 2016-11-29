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
package com.jetbrains.python.refactoring.classes;

import com.intellij.refactoring.classMembers.AbstractMemberInfoStorage;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.classes.membersManager.MembersManager;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;

import java.util.ArrayList;

/**
 * @author Dennis.Ushakov
 */
public class PyMemberInfoStorage extends AbstractMemberInfoStorage<PyElement, PyClass, PyMemberInfo<PyElement>> {

  public PyMemberInfoStorage(PyClass aClass) {
    this(aClass, new MemberInfoBase.EmptyFilter<>());
  }

  public PyMemberInfoStorage(PyClass aClass, MemberInfoBase.Filter<PyElement> memberInfoFilter) {
    super(aClass, memberInfoFilter);
  }

  @Override
  protected boolean isInheritor(PyClass baseClass, PyClass aClass) {
    return getSubclasses(baseClass).contains(aClass);
  }

  @Override
  protected void buildSubClassesMap(PyClass aClass) {
    buildSubClassesMapImpl(aClass, new HashSet<>());
  }

  private void buildSubClassesMapImpl(PyClass aClass, HashSet<PyClass> visited) {
    visited.add(aClass);
    for (PyClass clazz : aClass.getSuperClasses(null)) {
      getSubclasses(clazz).add(aClass);
      if (!visited.contains(clazz)) {
        buildSubClassesMapImpl(clazz, visited);
      }
    }
  }

  @Override
  protected void extractClassMembers(PyClass aClass, ArrayList<PyMemberInfo<PyElement>> temp) {
    temp.addAll(MembersManager.getAllMembersCouldBeMoved(aClass));
  }

  @Override
  protected boolean memberConflict(PyElement member1, PyElement member) {
    return member1 instanceof PyFunction && member instanceof PyFunction &&
           PyRefactoringUtil.areConflictingMethods((PyFunction)member, (PyFunction)member1);
  }
}
