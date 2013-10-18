package com.jetbrains.python.testing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Filters out Python unit tests for which it doesn't make sense to run the standard unit test configuration,
 * and which are (possibly) run by other configurations instead.
 *
 * @author yole
 */
public interface RunnableUnitTestFilter {
  ExtensionPointName<RunnableUnitTestFilter> EP_NAME = ExtensionPointName.create("Pythonid.runnableUnitTestFilter");

  boolean isRunnableUnitTest(PsiFile script, @NotNull Module module);
}