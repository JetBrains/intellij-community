/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.testing;

import com.intellij.execution.Location;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parent of all test configurations
 *
 * @author Ilya.Kazakevich
 */
public abstract class AbstractPythonTestRunConfiguration<T extends AbstractPythonTestRunConfiguration<T>>
  extends AbstractPythonRunConfiguration<T> {
  /**
   * When passing path to test to runners, you should join parts with this char.
   * I.e.: file.py::PyClassTest::test_method
   */
  protected static final String TEST_NAME_PARTS_SPLITTER = "::";

  protected AbstractPythonTestRunConfiguration(Project project, ConfigurationFactory factory) {
    super(project, factory);
  }

  /**
   * Create test spec (string to be passed to runner, probably glued with {@link AbstractPythonLegacyTestRunConfiguration#TEST_NAME_PARTS_SPLITTER})
   *
   * @param location   test location as reported by runner
   * @param failedTest failed test
   * @return string spec or null if spec calculation is impossible
   */
  @Nullable
  public String getTestSpec(@NotNull final Location<?> location, @NotNull final AbstractTestProxy failedTest) {
    PsiElement element = location.getPsiElement();
    PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    if (location instanceof PyPsiLocationWithFixedClass) {
      pyClass = ((PyPsiLocationWithFixedClass)location).getFixedClass();
    }
    PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
    final VirtualFile virtualFile = location.getVirtualFile();
    if (virtualFile != null) {
      String path = virtualFile.getCanonicalPath();
      if (pyClass != null) {
        path += TEST_NAME_PARTS_SPLITTER + pyClass.getName();
      }
      if (pyFunction != null) {
        path += TEST_NAME_PARTS_SPLITTER + pyFunction.getName();
      }
      return path;
    }
    return null;
  }
}
