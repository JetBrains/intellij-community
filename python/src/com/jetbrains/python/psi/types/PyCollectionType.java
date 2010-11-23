package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyCollectionType extends PyType {
  @Nullable
  PyType getElementType(TypeEvalContext context);
}
