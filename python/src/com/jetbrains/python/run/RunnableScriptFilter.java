package com.jetbrains.python.run;

import com.intellij.execution.Location;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Filters out Python scripts for which it doesn't make sense to run the standard Python configuration,
 * and which are (possibly) run by other configurations instead.
 *
 * @author yole
 */
public interface RunnableScriptFilter {
  ExtensionPointName<RunnableScriptFilter> EP_NAME = ExtensionPointName.create("Pythonid.runnableScriptFilter");

  boolean isRunnableScript(PsiFile script, @NotNull Module module, Location location);
}
