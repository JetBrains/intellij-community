package com.jetbrains.python.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.QualifiedNameResolver;
import com.jetbrains.python.psi.types.PyClassType;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class PyPsiFacade {
  public static PyPsiFacade getInstance(Project project) {
    return ServiceManager.getService(project, PyPsiFacade.class);
  }

  public abstract QualifiedNameResolver qualifiedNameResolver(String qNameString);
  public abstract QualifiedNameResolver qualifiedNameResolver(PyQualifiedName qualifiedName);

  @Nullable
  public abstract PyClass findClass(String qName);

  public abstract PyClassType createClassType(PyClass pyClass, boolean isDefinition);
}
