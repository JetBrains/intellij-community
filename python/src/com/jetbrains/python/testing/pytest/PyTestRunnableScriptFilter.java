package com.jetbrains.python.testing.pytest;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.run.RunnableScriptFilter;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyTestRunnableScriptFilter implements RunnableScriptFilter {
  public boolean isRunnableScript(PsiFile script, @NotNull Module module) {
    return isPyTestScript(script);
  }

  public static boolean isPyTestScript(PsiFile script) {
    if (!(script instanceof PyFile)) {
      return false;
    }
    PyTestVisitor testVisitor = new PyTestVisitor();
    script.accept(testVisitor);
    return testVisitor.isTestsFound();
  }

  private static class PyTestVisitor extends PyRecursiveElementVisitor {
    private boolean myTestsFound = false;

    public boolean isTestsFound() {
      return myTestsFound;
    }

    @Override
    public void visitPyFunction(PyFunction node) {
      super.visitPyFunction(node);
      String name = node.getName();
      if (name != null && name.startsWith("test")) {
        myTestsFound = true;
      }
    }

    @Override
    public void visitPyClass(PyClass node) {
      super.visitPyClass(node);
      String name = node.getName();
      if (name != null && name.startsWith("Test")) {
        myTestsFound = true;
      }
    }
  }
}
