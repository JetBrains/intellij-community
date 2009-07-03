package com.jetbrains.python.testing;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.run.RunnableScriptFilter;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonUnitTestRunnableScriptFilter implements RunnableScriptFilter {
  public boolean isRunnableScript(PsiFile script, @NotNull Module module) {
    return script instanceof PyFile && PythonUnitTestUtil.getTestCaseClassesFromFile((PyFile) script).size() > 0;
  }
}
