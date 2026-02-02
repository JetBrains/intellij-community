// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.impl.stubs.PyAnnotationElementType;
import com.jetbrains.python.psi.impl.stubs.PyClassElementType;
import com.jetbrains.python.psi.impl.stubs.PyDecoratorCallElementType;
import com.jetbrains.python.psi.impl.stubs.PyDecoratorListElementType;
import com.jetbrains.python.psi.impl.stubs.PyExceptPartElementType;
import com.jetbrains.python.psi.impl.stubs.PyFromImportStatementElementType;
import com.jetbrains.python.psi.impl.stubs.PyFunctionElementType;
import com.jetbrains.python.psi.impl.stubs.PyImportElementElementType;
import com.jetbrains.python.psi.impl.stubs.PyImportStatementElementType;
import com.jetbrains.python.psi.impl.stubs.PyNamedParameterElementType;
import com.jetbrains.python.psi.impl.stubs.PyParameterListElementType;
import com.jetbrains.python.psi.impl.stubs.PySingleStarParameterElementType;
import com.jetbrains.python.psi.impl.stubs.PySlashParameterElementType;
import com.jetbrains.python.psi.impl.stubs.PyStarImportElementElementType;
import com.jetbrains.python.psi.impl.stubs.PyTargetExpressionElementType;
import com.jetbrains.python.psi.impl.stubs.PyTupleParameterElementType;
import com.jetbrains.python.psi.impl.stubs.PyTypeAliasStatementElementType;
import com.jetbrains.python.psi.impl.stubs.PyTypeParameterElementType;
import com.jetbrains.python.psi.impl.stubs.PyTypeParameterListElementType;
import com.jetbrains.python.psi.stubs.PyAnnotationStub;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import com.jetbrains.python.psi.stubs.PySingleStarParameterStub;
import com.jetbrains.python.psi.stubs.PySlashParameterStub;
import com.jetbrains.python.psi.stubs.PyStarImportElementStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;
import com.jetbrains.python.psi.stubs.PyTypeAliasStatementStub;
import com.jetbrains.python.psi.stubs.PyTypeParameterListStub;
import com.jetbrains.python.psi.stubs.PyTypeParameterStub;

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
