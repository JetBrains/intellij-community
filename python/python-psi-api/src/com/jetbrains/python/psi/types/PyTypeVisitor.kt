// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import org.jetbrains.annotations.ApiStatus

/**
 * Similarly to [com.intellij.psi.PsiElementVisitor], implements double dispatching for the [PyType] hierarchy.
 * 
 * 
 * Because the "unknown" type is historically represented as `null` in the type system, `PyTypeVisitor.visitPyType(type, visitor)`
 * should be used instead of direct `type.acceptTypeVisitor(visitor)` to properly account for possible `null` values.
 * 
 * 
 * This class gives access only to the types declared in the <tt>intellij.python.psi</tt> module.
 * Most actual implementations should extend [PyTypeVisitorExt].
 * 
 * 
 * There are helper [PyRecursiveTypeVisitor] and [PyCloningTypeVisitor] for recursive type
 * traversal and deep cloning of a type respectively.
 * 
 * @see PyRecursiveTypeVisitor
 * 
 * @see PyCloningTypeVisitor
 * 
 * @see PyType.acceptTypeVisitor
 * @see .visit
 * @see .visitUnknownType
 */
@ApiStatus.Experimental
abstract class PyTypeVisitor<T> {
  open fun visitPyType(type: PyType): T? {
    return null
  }

  open fun visitPyClassType(classType: PyClassType): T? {
    return visitPyClassLikeType(classType)
  }

  open fun visitPyClassLikeType(classLikeType: PyClassLikeType): T? {
    // Don't treat PyClassLikeType as PyCallableType. It's usually not what a visitor's user expects.
    return visitPyType(classLikeType)
  }

  open fun visitPyFunctionType(functionType: PyFunctionType): T? {
    return visitPyCallableType(functionType)
  }

  open fun visitPyCallableType(callableType: PyCallableType): T? {
    return visitPyType(callableType)
  }

  open fun visitPyTypeVarType(typeVarType: PyTypeVarType): T? {
    return visitPyTypeParameterType(typeVarType)
  }

  open fun visitPyTypeVarTupleType(typeVarTupleType: PyTypeVarTupleType): T? {
    return visitPyTypeParameterType(typeVarTupleType)
  }

  open fun visitPyTypeParameterType(typeParameterType: PyTypeParameterType): T? {
    return visitPyType(typeParameterType)
  }

  open fun visitPyUnpackedTupleType(unpackedTupleType: PyUnpackedTupleType): T? {
    return visitPyType(unpackedTupleType)
  }

  open fun visitPyCallableParameterListType(callableParameterListType: PyCallableParameterListType): T? {
    return visitPyType(callableParameterListType)
  }

  open fun visitPyNeverType(neverType: PyNeverType): T? {
    return visitPyType(neverType)
  }

  open fun visitAnyType(): T? {
    return null
  }

  open fun visitUnknownType(): T? {
    return null
  }

  companion object {
    /**
     * Use this method instead of [PyType.acceptTypeVisitor] to take into account
     * "unknown" `null` types, for which [.visitUnknownType] should be called.
     */
    @JvmStatic
    fun <T> visit(type: PyType?, visitor: PyTypeVisitor<T>): T? =
      if (type == null) visitor.visitUnknownType() else type.acceptTypeVisitor(visitor)
  }
}
