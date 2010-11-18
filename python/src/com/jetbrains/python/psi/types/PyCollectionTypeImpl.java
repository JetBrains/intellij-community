package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyClass;
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

  @Override
  public PyType getElementType(TypeEvalContext context) {
    return myElementType;
  }
}
