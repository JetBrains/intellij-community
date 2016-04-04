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

/*
 * User: catherine
 */
package com.jetbrains.python.testing.doctest;

import com.intellij.execution.Location;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.testing.PythonTestConfigurationProducer;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PythonDocTestConfigurationProducer extends PythonTestConfigurationProducer {

  public PythonDocTestConfigurationProducer() {
    super(PythonTestConfigurationType.getInstance().PY_DOCTEST_FACTORY);
  }

  @Override
  protected boolean isTestFunction(@NotNull final PyFunction pyFunction, @Nullable final AbstractPythonTestRunConfiguration configuration) {
    return PythonDocTestUtil.isDocTestFunction(pyFunction);
  }

  @Override
  protected boolean isTestClass(@NotNull PyClass pyClass,
                                @Nullable final AbstractPythonTestRunConfiguration configuration,
                                @Nullable final TypeEvalContext context) {
    return PythonDocTestUtil.isDocTestClass(pyClass);
  }

  @Override
  protected boolean isTestFile(@NotNull PyFile file) {
    final List<PyElement> testCases = PythonDocTestUtil.getDocTestCasesFromFile(file);
    return !testCases.isEmpty();
  }

  protected boolean isAvailable(@NotNull final Location location) {
    final Module module = location.getModule();
    if (!isPythonModule(module)) return false;
    final PsiElement element = location.getPsiElement();
    if (element instanceof PsiFile) {
      final PyDocTestVisitor visitor = new PyDocTestVisitor();
      element.accept(visitor);
      return visitor.hasTests;
    }
    else return true;
  }

  private static class PyDocTestVisitor extends PsiRecursiveElementVisitor {
    boolean hasTests = false;

    @Override
    public void visitFile(PsiFile node) {
      if (node instanceof PyFile) {
        List<PyElement> testClasses = PythonDocTestUtil.getDocTestCasesFromFile((PyFile)node);
        if (!testClasses.isEmpty()) hasTests = true;
      }
      else {
        final String text = node.getText();
        if (PythonDocTestUtil.hasExample(text)) hasTests = true;
      }
    }
  }

  @Override
  protected boolean isTestFolder(@NotNull VirtualFile virtualFile, @NotNull Project project) {
    return false;
  }

}