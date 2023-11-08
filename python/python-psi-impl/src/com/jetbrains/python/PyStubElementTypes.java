// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.stubs.*;
import com.jetbrains.python.psi.stubs.*;

public interface PyStubElementTypes {
  PyStubElementType<PyFunctionStub, PyFunction> FUNCTION_DECLARATION = new PyFunctionElementType();
  PyStubElementType<PyClassStub, PyClass> CLASS_DECLARATION = new PyClassElementType();
  PyStubElementType<PyParameterListStub, PyParameterList> PARAMETER_LIST = new PyParameterListElementType();

  PyStubElementType<PyDecoratorListStub, PyDecoratorList> DECORATOR_LIST = new PyDecoratorListElementType();

  PyStubElementType<PyNamedParameterStub, PyNamedParameter> NAMED_PARAMETER = new PyNamedParameterElementType();
  PyStubElementType<PyTupleParameterStub, PyTupleParameter> TUPLE_PARAMETER = new PyTupleParameterElementType();
  PyStubElementType<PySlashParameterStub, PySlashParameter> SLASH_PARAMETER = new PySlashParameterElementType();
  PyStubElementType<PySingleStarParameterStub, PySingleStarParameter> SINGLE_STAR_PARAMETER = new PySingleStarParameterElementType();

  PyStubElementType<PyDecoratorStub, PyDecorator> DECORATOR_CALL = new PyDecoratorCallElementType();

  PyStubElementType<PyImportElementStub, PyImportElement> IMPORT_ELEMENT = new PyImportElementElementType();

  PyStubElementType<PyAnnotationStub, PyAnnotation> ANNOTATION = new PyAnnotationElementType();

  PyStubElementType<PyStarImportElementStub, PyStarImportElement> STAR_IMPORT_ELEMENT = new PyStarImportElementElementType();
  PyStubElementType<PyExceptPartStub, PyExceptPart> EXCEPT_PART = new PyExceptPartElementType();

  PyStubElementType<PyFromImportStatementStub, PyFromImportStatement> FROM_IMPORT_STATEMENT = new PyFromImportStatementElementType();
  PyStubElementType<PyImportStatementStub, PyImportStatement> IMPORT_STATEMENT = new PyImportStatementElementType();

  PyStubElementType<PyTargetExpressionStub, PyTargetExpression> TARGET_EXPRESSION = new PyTargetExpressionElementType();
  PyStubElementType<PyTypeParameterStub, PyTypeParameter> TYPE_PARAMETER = new PyTypeParameterElementType();
  PyStubElementType<PyTypeParameterListStub, PyTypeParameterList> TYPE_PARAMETER_LIST = new PyTypeParameterListElementType();
  PyStubElementType<PyTypeAliasStatementStub, PyTypeAliasStatement> TYPE_ALIAS_STATEMENT = new PyTypeAliasStatementElementType();
}
