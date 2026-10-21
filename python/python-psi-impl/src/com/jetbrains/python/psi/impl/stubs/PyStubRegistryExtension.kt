// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubRegistry
import com.intellij.psi.stubs.StubRegistryExtension
import com.jetbrains.python.PythonParserDefinition
import com.jetbrains.python.pyi.PyiParserDefinition

/**
 * Binds the stub factories of the Python PSI to the shared element types of `intellij.python.parser`, and the file stub
 * serializers to the file node types of the Python and the PythonStub parser definitions.
 * Each factory names its element type, see [PyStubElementFactory.register].
 */
internal class PyStubRegistryExtension : StubRegistryExtension {
  override fun register(registry: StubRegistry) {
    val factories = listOf(
      PyFunctionElementType(),
      PyClassElementType(),
      PyParameterListElementType(),
      PyDecoratorListElementType(),
      PyNamedParameterElementType(),
      PyTupleParameterElementType(),
      PySlashParameterElementType(),
      PySingleStarParameterElementType(),
      PyDecoratorCallElementType(),
      PyImportElementElementType(),
      PyAnnotationElementType(),
      PyStarImportElementElementType(),
      PyExceptPartElementType(),
      PyFromImportStatementElementType(),
      PyImportStatementElementType(),
      PyTargetExpressionElementType(),
      PyTypeParameterElementType(),
      PyTypeParameterListElementType(),
      PyTypeAliasStatementElementType(),
    )
    for (factory in factories) {
      factory.register(registry)
    }

    registry.registerStubSerializer(PythonParserDefinition.PYTHON_FILE,
                                    PyFileStubSerializer(PythonParserDefinition.PYTHON_FILE, "python.FILE"))
    registry.registerStubSerializer(PyiParserDefinition.PYTHON_STUB_FILE,
                                    PyFileStubSerializer(PyiParserDefinition.PYTHON_STUB_FILE, "PythonStub.FILE"))
  }
}
