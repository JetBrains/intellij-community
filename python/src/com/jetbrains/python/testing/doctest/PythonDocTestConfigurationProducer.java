/*
 * User: catherine
 */
package com.jetbrains.python.testing.doctest;

import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.testing.PythonTestConfigurationProducer;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PythonDocTestConfigurationProducer extends PythonTestConfigurationProducer {

  public PythonDocTestConfigurationProducer() {
    super(PythonTestConfigurationType.getInstance().PY_DOCTEST_FACTORY);
  }

  protected boolean isTestFunction(PyFunction pyFunction) {
    if (pyFunction == null || !PythonDocTestUtil.isDocTestFunction(pyFunction)) return false;
    return true;
  }

  protected boolean isTestClass(PyClass pyClass) {
    if (pyClass == null || !PythonDocTestUtil.isDocTestClass(pyClass)) return false;
    return true;
  }

  @Nullable
  protected RunnerAndConfigurationSettings createConfigurationFromFile(Location location, PsiElement element) {
    PsiElement file = element.getContainingFile();
    if (file == null || !(file instanceof PyFile)) return null;

    final PyFile pyFile = (PyFile)file;
    final List<PyElement> testCases = PythonDocTestUtil.getDocTestCasesFromFile(pyFile);
    if (testCases.isEmpty()) return null;

    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "doc tests from file");
    final PythonDocTestRunConfiguration configuration = (PythonDocTestRunConfiguration)settings.getConfiguration();

    configuration.setTestType(PythonDocTestRunConfiguration.TestType.TEST_SCRIPT);
    if (!setupConfigurationScript(configuration, pyFile)) return null;
    configuration.setName(configuration.suggestedName());
    myPsiElement = pyFile;
    return settings;
  }

  protected boolean isAvailable(Location location) {
    final Module module = location.getModule();
    if (!isPythonModule(module)) return false;
    final PsiElement element = location.getPsiElement();
    if (element instanceof PsiDirectory) {
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
}