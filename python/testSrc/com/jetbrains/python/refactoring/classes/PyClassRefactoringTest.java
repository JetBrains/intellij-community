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
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Collection;

/**
 * @author Dennis.Ushakov
 */
public abstract class PyClassRefactoringTest extends PyTestCase {
  /**
   * @param className  class where member should be found
   * @param memberName member that starts with dot (<code>.</code>) is treated as method.
   *                   It is treated parent class otherwise
   * @return member or null if not found
   */
  @NotNull
  protected PyElement findMember(@NotNull String className, @NotNull String memberName) {
    boolean findMethod = memberName.contains(".");
    PyElement result = (findMethod ? findMethod(className, memberName.substring(1)) : findClass(memberName));
    Assert.assertNotNull(String.format("No member %s found in class %s", memberName, className), result);
    return result;
  }

  private PyFunction findMethod(final String className, final String name) {
    final PyClass clazz = findClass(className);
    return clazz.findMethodByName(name, false);
  }

  protected PyClass findClass(final String name) {
    final Project project = myFixture.getProject();
    final Collection<PyClass> classes = PyClassNameIndex.find(name, project, false);
    Assert.assertThat(String.format("Expected one class named %s", name), classes, Matchers.hasSize(1));
    return classes.iterator().next();
  }
}
