/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PyCollectionType)) return false;
    if (!super.equals(o)) return false;

    PyCollectionType type = (PyCollectionType)o;

    final TypeEvalContext context = TypeEvalContext.codeInsightFallback();
    if (myElementType != null ? !myElementType.equals(type.getElementType(context)) : type.getElementType(context) != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myElementType != null ? myElementType.hashCode() : 0);
    return result;
  }
}
