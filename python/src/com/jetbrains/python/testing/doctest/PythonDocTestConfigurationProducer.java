// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.jetbrains.python.testing.doctest;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.ConfigurationFactory;
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
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import com.jetbrains.python.testing.PythonTestLegacyConfigurationProducer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PythonDocTestConfigurationProducer extends PythonTestLegacyConfigurationProducer {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return PythonTestConfigurationType.getInstance().PY_DOCTEST_FACTORY;
  }

  @Override
  protected boolean isTestFunction(@NotNull final PyFunction pyFunction, @Nullable final AbstractPythonLegacyTestRunConfiguration configuration) {
    return PythonDocTestUtil.isDocTestFunction(pyFunction);
  }

  @Override
  protected boolean isTestClass(@NotNull PyClass pyClass,
                                @Nullable final AbstractPythonLegacyTestRunConfiguration configuration,
                                @Nullable final TypeEvalContext context) {
    return PythonDocTestUtil.isDocTestClass(pyClass);
  }

  @Override
  protected boolean isTestFile(@NotNull PyFile file) {
    final List<PyElement> testCases = PythonDocTestUtil.getDocTestCasesFromFile(file);
    return !testCases.isEmpty();
  }

  @Override
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

  // test configuration is always prefered over regular one
  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return self.isProducedBy(getClass());
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