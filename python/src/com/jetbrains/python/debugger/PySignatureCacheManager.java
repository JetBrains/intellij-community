package com.jetbrains.python.debugger;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public abstract class PySignatureCacheManager {


  public static PySignatureCacheManager getInstance(Project project) {
    return ServiceManager.getService(project, PySignatureCacheManager.class);
  }

  public abstract void recordSignature(@NotNull PySignature signature);

  @Nullable
  public abstract String findParameterType(@NotNull PyFunction function, @NotNull String name);

  @Nullable
  public abstract PySignature findSignature(@NotNull PyFunction function);

  public abstract void clearCache();
}
