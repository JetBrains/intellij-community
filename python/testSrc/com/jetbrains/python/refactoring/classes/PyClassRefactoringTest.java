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

import com.intellij.openapi.project.Project;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;

import java.util.Collection;

/**
 * @author Dennis.Ushakov
 */
public abstract class PyClassRefactoringTest extends PyTestCase {
  protected PyElement findMember(String className, String memberName) {
    if (!memberName.contains(".")) return findClass(memberName);
    return findMethod(className, memberName.substring(1));
  }

  private PyFunction findMethod(final String className, final String name) {
    final PyClass clazz = findClass(className);
    final PyFunction method = clazz.findMethodByName(name, false);
    assertNotNull(method);
    return method;
  }

  protected PyClass findClass(final String name) {
    final Project project = myFixture.getProject();
    final Collection<PyClass> classes = PyClassNameIndex.find(name, project, false);
    assertEquals(1, classes.size());
    return classes.iterator().next();
  }
}
