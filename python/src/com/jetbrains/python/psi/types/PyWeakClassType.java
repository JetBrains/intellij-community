package com.jetbrains.python.psi.types;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class PyWeakClassType extends PyClassType implements PyWeakType {
  public PyWeakClassType(@Nullable PyClass source, boolean is_definition) {
    super(source, is_definition);
  }

  public PyWeakClassType(@NotNull Project project, String classQualifiedName, boolean isDefinition) {
    super(project, classQualifiedName, isDefinition);
  }
}
