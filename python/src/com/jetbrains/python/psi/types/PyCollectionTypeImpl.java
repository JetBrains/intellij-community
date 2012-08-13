package com.jetbrains.python.psi.types;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyCollectionTypeImpl extends PyClassTypeImpl implements PyCollectionType {
  private final PyType myElementType;

  public PyCollectionTypeImpl(@NotNull PyClass source, boolean isDefinition, PyType elementType) {
    super(source, isDefinition);
    myElementType = elementType;
  }

  @Override
  public PyType getElementType(TypeEvalContext context) {
    return myElementType;
  }

  @Nullable
  public static PyCollectionTypeImpl createTypeByQName(@NotNull Project project, String classQualifiedName, boolean isDefinition,
                                                       PyType elementType) {
    PyClass pyClass = PyClassNameIndex.findClass(classQualifiedName, project);
    if (pyClass == null) {
      return null;
    }
    return new PyCollectionTypeImpl(pyClass, isDefinition, elementType);
  }
}
