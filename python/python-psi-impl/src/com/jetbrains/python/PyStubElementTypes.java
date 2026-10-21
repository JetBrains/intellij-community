// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.psi.tree.IElementType;

/**
 * The element types that have a stub. They are the constants of {@link PyElementTypes}; the stub factories of
 * {@link com.jetbrains.python.psi.impl.stubs.PyStubRegistryExtension} bind to them.
 */
public interface PyStubElementTypes {
  IElementType FUNCTION_DECLARATION = PyElementTypes.FUNCTION_DECLARATION;
  IElementType CLASS_DECLARATION = PyElementTypes.CLASS_DECLARATION;
  IElementType PARAMETER_LIST = PyElementTypes.PARAMETER_LIST;

  IElementType DECORATOR_LIST = PyElementTypes.DECORATOR_LIST;

  IElementType NAMED_PARAMETER = PyElementTypes.NAMED_PARAMETER;
  IElementType TUPLE_PARAMETER = PyElementTypes.TUPLE_PARAMETER;
  IElementType SLASH_PARAMETER = PyElementTypes.SLASH_PARAMETER;
  IElementType SINGLE_STAR_PARAMETER = PyElementTypes.SINGLE_STAR_PARAMETER;

  IElementType DECORATOR_CALL = PyElementTypes.DECORATOR_CALL;

  IElementType IMPORT_ELEMENT = PyElementTypes.IMPORT_ELEMENT;

  IElementType ANNOTATION = PyElementTypes.ANNOTATION;

  IElementType STAR_IMPORT_ELEMENT = PyElementTypes.STAR_IMPORT_ELEMENT;
  IElementType EXCEPT_PART = PyElementTypes.EXCEPT_PART;

  IElementType FROM_IMPORT_STATEMENT = PyElementTypes.FROM_IMPORT_STATEMENT;
  IElementType IMPORT_STATEMENT = PyElementTypes.IMPORT_STATEMENT;

  IElementType TARGET_EXPRESSION = PyElementTypes.TARGET_EXPRESSION;
  IElementType TYPE_PARAMETER = PyElementTypes.TYPE_PARAMETER;
  IElementType TYPE_PARAMETER_LIST = PyElementTypes.TYPE_PARAMETER_LIST;
  IElementType TYPE_ALIAS_STATEMENT = PyElementTypes.TYPE_ALIAS_STATEMENT;
}
