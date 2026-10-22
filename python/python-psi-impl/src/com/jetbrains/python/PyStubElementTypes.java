// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.jetbrains.python.psi.impl.stubs.PyLegacyStubElementType;
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


/**
 * The legacy {@link PyStubElementType} view of the stub element types, kept for an external plugin.
 * <p>
 * The Python PSI uses the plain constants of {@link PyElementTypes} as the node types, and binds the stubs to them through
 * {@link com.jetbrains.python.psi.impl.stubs.PyStubRegistryExtension}. Each constant here is a separate
 * {@link PyLegacyStubElementType} that delegates to the same factory. It is not the node type of any tree node, so a comparison
 * with {@code node.getElementType()} fails. Use {@link PyElementTypes} instead.
 * <p>
 * The {@code stubElementTypeHolder} registration of this interface derives the external id of each constant from the field name.
 * Keep the field names.
 */
public interface PyStubElementTypes {
  /** @deprecated use {@link PyElementTypes#FUNCTION_DECLARATION}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyFunctionStub, PyFunction> FUNCTION_DECLARATION =
    new PyLegacyStubElementType<>(PyElementTypes.FUNCTION_DECLARATION, new PyFunctionElementType());
  /** @deprecated use {@link PyElementTypes#CLASS_DECLARATION}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyClassStub, PyClass> CLASS_DECLARATION =
    new PyLegacyStubElementType<>(PyElementTypes.CLASS_DECLARATION, new PyClassElementType());
  /** @deprecated use {@link PyElementTypes#PARAMETER_LIST}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyParameterListStub, PyParameterList> PARAMETER_LIST =
    new PyLegacyStubElementType<>(PyElementTypes.PARAMETER_LIST, new PyParameterListElementType());

  /** @deprecated use {@link PyElementTypes#DECORATOR_LIST}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyDecoratorListStub, PyDecoratorList> DECORATOR_LIST =
    new PyLegacyStubElementType<>(PyElementTypes.DECORATOR_LIST, new PyDecoratorListElementType());

  /** @deprecated use {@link PyElementTypes#NAMED_PARAMETER}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyNamedParameterStub, PyNamedParameter> NAMED_PARAMETER =
    new PyLegacyStubElementType<>(PyElementTypes.NAMED_PARAMETER, new PyNamedParameterElementType());
  /** @deprecated use {@link PyElementTypes#TUPLE_PARAMETER}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyTupleParameterStub, PyTupleParameter> TUPLE_PARAMETER =
    new PyLegacyStubElementType<>(PyElementTypes.TUPLE_PARAMETER, new PyTupleParameterElementType());
  /** @deprecated use {@link PyElementTypes#SLASH_PARAMETER}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PySlashParameterStub, PySlashParameter> SLASH_PARAMETER =
    new PyLegacyStubElementType<>(PyElementTypes.SLASH_PARAMETER, new PySlashParameterElementType());
  /** @deprecated use {@link PyElementTypes#SINGLE_STAR_PARAMETER}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PySingleStarParameterStub, PySingleStarParameter> SINGLE_STAR_PARAMETER =
    new PyLegacyStubElementType<>(PyElementTypes.SINGLE_STAR_PARAMETER, new PySingleStarParameterElementType());

  /** @deprecated use {@link PyElementTypes#DECORATOR_CALL}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyDecoratorStub, PyDecorator> DECORATOR_CALL =
    new PyLegacyStubElementType<>(PyElementTypes.DECORATOR_CALL, new PyDecoratorCallElementType());

  /** @deprecated use {@link PyElementTypes#IMPORT_ELEMENT}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyImportElementStub, PyImportElement> IMPORT_ELEMENT =
    new PyLegacyStubElementType<>(PyElementTypes.IMPORT_ELEMENT, new PyImportElementElementType());

  /** @deprecated use {@link PyElementTypes#ANNOTATION}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyAnnotationStub, PyAnnotation> ANNOTATION =
    new PyLegacyStubElementType<>(PyElementTypes.ANNOTATION, new PyAnnotationElementType());

  /** @deprecated use {@link PyElementTypes#STAR_IMPORT_ELEMENT}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyStarImportElementStub, PyStarImportElement> STAR_IMPORT_ELEMENT =
    new PyLegacyStubElementType<>(PyElementTypes.STAR_IMPORT_ELEMENT, new PyStarImportElementElementType());
  /** @deprecated use {@link PyElementTypes#EXCEPT_PART}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyExceptPartStub, PyExceptPart> EXCEPT_PART =
    new PyLegacyStubElementType<>(PyElementTypes.EXCEPT_PART, new PyExceptPartElementType());

  /** @deprecated use {@link PyElementTypes#FROM_IMPORT_STATEMENT}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyFromImportStatementStub, PyFromImportStatement> FROM_IMPORT_STATEMENT =
    new PyLegacyStubElementType<>(PyElementTypes.FROM_IMPORT_STATEMENT, new PyFromImportStatementElementType());
  /** @deprecated use {@link PyElementTypes#IMPORT_STATEMENT}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyImportStatementStub, PyImportStatement> IMPORT_STATEMENT =
    new PyLegacyStubElementType<>(PyElementTypes.IMPORT_STATEMENT, new PyImportStatementElementType());

  /** @deprecated use {@link PyElementTypes#TARGET_EXPRESSION}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyTargetExpressionStub, PyTargetExpression> TARGET_EXPRESSION =
    new PyLegacyStubElementType<>(PyElementTypes.TARGET_EXPRESSION, new PyTargetExpressionElementType());
  /** @deprecated use {@link PyElementTypes#TYPE_PARAMETER}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyTypeParameterStub, PyTypeParameter> TYPE_PARAMETER =
    new PyLegacyStubElementType<>(PyElementTypes.TYPE_PARAMETER, new PyTypeParameterElementType());
  /** @deprecated use {@link PyElementTypes#TYPE_PARAMETER_LIST}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyTypeParameterListStub, PyTypeParameterList> TYPE_PARAMETER_LIST =
    new PyLegacyStubElementType<>(PyElementTypes.TYPE_PARAMETER_LIST, new PyTypeParameterListElementType());
  /** @deprecated use {@link PyElementTypes#TYPE_ALIAS_STATEMENT}. */
  @Deprecated(forRemoval = true)
  PyStubElementType<PyTypeAliasStatementStub, PyTypeAliasStatement> TYPE_ALIAS_STATEMENT =
    new PyLegacyStubElementType<>(PyElementTypes.TYPE_ALIAS_STATEMENT, new PyTypeAliasStatementElementType());
}
