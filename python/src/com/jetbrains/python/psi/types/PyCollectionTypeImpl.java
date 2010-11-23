package com.jetbrains.python.psi.types;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyCollectionTypeImpl extends PyClassType implements PyCollectionType {
  private final PyType myElementType;

  public PyCollectionTypeImpl(@Nullable PyClass source, boolean isDefinition, PyType elementType) {
    super(source, isDefinition);
    myElementType = elementType;
  }

  public PyCollectionTypeImpl(@NotNull Project project, String classQualifiedName, boolean isDefinition, PyType elementType) {
    super(project, classQualifiedName, isDefinition);
    myElementType = elementType;
  }

  @Override
  public PyType getElementType(TypeEvalContext context) {
    return myElementType;
  }
}
