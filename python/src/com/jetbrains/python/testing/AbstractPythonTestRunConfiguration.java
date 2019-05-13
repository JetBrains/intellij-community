// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  protected AbstractPythonTestRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory) {
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
