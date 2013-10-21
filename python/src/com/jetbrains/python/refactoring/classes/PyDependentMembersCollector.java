/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;

/**
 * @author Dennis.Ushakov
 */
public class PyDependentMembersCollector extends DependentMembersCollectorBase<PyElement,PyClass> {
  public PyDependentMembersCollector(PyClass clazz, PyClass superClass) {
    super(clazz, superClass);
  }

  @Override
  public void collect(PyElement member) {
    final PyRecursiveElementVisitor visitor = new PyRecursiveElementVisitor() {
      @Override
      public void visitPyCallExpression(PyCallExpression node) {
        final Callable markedFunction = node.resolveCalleeFunction(PyResolveContext.noImplicits());
        final PyFunction function = markedFunction != null ? markedFunction.asMethod() : null;
        if (!existsInSuperClass(function)) {
          myCollection.add(function);
        }
      }
    };
    member.accept(visitor);
  }

  private boolean existsInSuperClass(PyFunction classMember) {
    if (getSuperClass() == null) return false;
    final String name = classMember != null ? classMember.getName() : null;
    if (name == null) return false;
    final PyFunction methodBySignature = (getSuperClass()).findMethodByName(name, true);
    return methodBySignature != null;
  }
}
