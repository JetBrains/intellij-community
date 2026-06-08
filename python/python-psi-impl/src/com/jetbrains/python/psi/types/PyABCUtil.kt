/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.psi.types

import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.types.PyTypeUtil.toStream

object PyABCUtil {
  @JvmStatic
  fun isSubclass(subClass: PyClass, superClass: PyClass, context: TypeEvalContext?): Boolean {
    val superName = superClass.name
    if (superName != null) {
      return isSubclass(subClass, superName, true, context)
    }
    return false
  }

  @JvmStatic
  fun isSubclass(subClass: PyClass, superClassName: String, context: TypeEvalContext?): Boolean {
    return isSubclass(subClass, superClassName, true, context)
  }

  @JvmStatic
  fun isSubclass(
    subClass: PyClass,
    superClassName: String,
    inherited: Boolean,
    context: TypeEvalContext?,
  ): Boolean {
    if (PyNames.CALLABLE == superClassName) {
      return hasMethod(subClass, PyNames.CALL, inherited, context)
    }
    if (PyNames.HASHABLE == superClassName) {
      return hasMethod(subClass, PyNames.HASH, inherited, context)
    }
    val hasIter = hasMethod(subClass, PyNames.ITER, inherited, context)
    val hasGetItem = hasMethod(subClass, PyNames.GETITEM, inherited, context)
    if (PyNames.ITERABLE == superClassName) {
      return hasIter || hasGetItem
    }
    if (PyNames.ITERATOR == superClassName) {
      return (hasIter && (hasMethod(subClass, PyNames.NEXT, inherited, context) || hasMethod(
        subClass,
        PyNames.DUNDER_NEXT, inherited, context
      ))) ||
             hasGetItem
    }
    val isSized = hasMethod(subClass, PyNames.LEN, inherited, context)
    if (PyNames.SIZED == superClassName) {
      return isSized
    }
    val isContainer = hasMethod(subClass, PyNames.CONTAINS, inherited, context)
    if (PyNames.CONTAINER == superClassName) {
      return isContainer
    }
    if (PyNames.SEQUENCE == superClassName) {
      return isSized && hasIter && isContainer && hasGetItem
    }
    if (PyNames.MAPPING == superClassName) {
      return isSized && hasIter && isContainer && hasGetItem && hasMethod(subClass, PyNames.KEYS, inherited, context)
    }
    if (PyNames.MUTABLE_MAPPING == superClassName) {
      val hasSetItem = hasMethod(subClass, PyNames.SETITEM, inherited, context)
      val hasUpdate = hasMethod(subClass, PyNames.UPDATE, inherited, context)
      return isSized && hasIter && isContainer && hasGetItem && hasSetItem && hasUpdate
    }
    if (PyNames.ABC_SET == superClassName) {
      return isSized && hasIter && isContainer
    }
    if (PyNames.ABC_MUTABLE_SET == superClassName) {
      return isSized && hasIter && isContainer &&
             hasMethod(subClass, "discard", inherited, context) &&
             hasMethod(subClass, "add", inherited, context)
    }
    if (PyNames.ABC_COMPLEX == superClassName) {
      return hasMethod(subClass, PyNames.COMPLEX, inherited, context)
    }
    if (PyNames.ABC_REAL == superClassName) {
      return hasMethod(subClass, PyNames.FLOAT, inherited, context)
    }
    if (PyNames.ABC_INTEGRAL == superClassName) {
      return hasMethod(subClass, PyNames.INT, inherited, context)
    }
    if (PyNames.ABC_NUMBER == superClassName && "Decimal" == subClass.name) {
      return true
    }
    if (PyNames.ASYNC_ITERABLE == superClassName) {
      return hasMethod(subClass, PyNames.AITER, inherited, context)
    }
    if (PyNames.AWAITABLE == superClassName) {
      return hasMethod(subClass, PyNames.DUNDER_AWAIT, inherited, context)
    }
    if (PyNames.ABSTRACT_CONTEXT_MANAGER == superClassName) {
      return hasMethod(subClass, PyNames.ENTER, inherited, context) && hasMethod(subClass, PyNames.EXIT, inherited, context)
    }
    if (PyNames.ABSTRACT_ASYNC_CONTEXT_MANAGER == superClassName) {
      return hasMethod(subClass, PyNames.AENTER, inherited, context) && hasMethod(subClass, PyNames.AEXIT, inherited, context)
    }
    return false
  }

  @JvmStatic
  fun isSubtype(type: PyType, superClassName: String, context: TypeEvalContext): Boolean {
    if (type is PyStructuralType) {
      // TODO: Convert abc types to structural types and check them properly
      return true
    }
    if (type is PyClassType) {
      val pyClass = type.pyClass
      if (type.isDefinition) {
        val metaClassType = type.getMetaClassType(context, true)
        if (metaClassType is PyClassType) {
          return isSubclass(metaClassType.pyClass, superClassName, true, context)
        }
      }
      else {
        return isSubclass(pyClass, superClassName, true, context)
      }
    }
    return when (type) {
      is PyUnionType -> {
        if (!PyUnionType.isStrictSemanticsEnabled()) {
          type.toStream().nonNull().anyMatch { isSubtype(it!!, superClassName, context) }
        }
        else type.toStream().nonNull().allMatch { isSubtype(it!!, superClassName, context) }
      }
      is PyUnsafeUnionType -> type.toStream().nonNull().anyMatch { isSubtype(it!!, superClassName, context) }
      is PyIntersectionType -> type.toStream().nonNull().anyMatch { isSubtype(it!!, superClassName, context) }
      else -> false
    }
  }

  private fun hasMethod(cls: PyClass, name: String, inherited: Boolean, context: TypeEvalContext?): Boolean {
    return cls.findMethodByName(name, inherited, context) != null || cls.findClassAttribute(name, inherited, context) != null
  }
}
