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

import com.intellij.refactoring.classMembers.DependentMembersCollectorBase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.MembersManager;

/**
 * @author Dennis.Ushakov
 */
public class PyDependentClassMembersCollector extends DependentMembersCollectorBase<PyElement,PyClass> {
  public PyDependentClassMembersCollector(PyClass clazz, PyClass superClass) {
    super(clazz, superClass);
  }

  @Override
  public void collect(final PyElement member) {
    myCollection.addAll(MembersManager.getAllDependencies(myClass, member, getSuperClass()));
  }
}
