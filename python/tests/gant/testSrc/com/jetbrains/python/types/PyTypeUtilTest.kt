// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyCollectionTypeImpl
import com.jetbrains.python.psi.types.PyIntersectionType
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.PyOverloadType
import com.jetbrains.python.psi.types.PyTopType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.PyTypeUtil.asUnionSequence
import com.jetbrains.python.psi.types.PyTypeUtil.collectTypeComponents
import com.jetbrains.python.psi.types.PyTypeUtil.compositeComponentSequence
import com.jetbrains.python.psi.types.PyTypeUtil.compositeComponents
import com.jetbrains.python.psi.types.PyTypeUtil.compositeMap
import com.jetbrains.python.psi.types.PyTypeUtil.compositeMatchesAsSubtype
import com.jetbrains.python.psi.types.PyTypeUtil.compositeMatchesAsSupertype
import com.jetbrains.python.psi.types.PyTypeUtil.compositeTransform
import com.jetbrains.python.psi.types.PyTypeUtil.derefOrUnknown
import com.jetbrains.python.psi.types.PyTypeUtil.findData
import com.jetbrains.python.psi.types.PyTypeUtil.getMembers
import com.jetbrains.python.psi.types.PyTypeUtil.isDict
import com.jetbrains.python.psi.types.PyTypeUtil.isSameType
import com.jetbrains.python.psi.types.PyTypeUtil.isSubtypeRelated
import com.jetbrains.python.psi.types.PyTypeUtil.notNullToRef
import com.jetbrains.python.psi.types.PyTypeUtil.toKeywordContainerType
import com.jetbrains.python.psi.types.PyTypeUtil.toPositionalContainerType
import com.jetbrains.python.psi.types.PyTypeVarTupleTypeImpl
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.PyUnpackedTupleTypeImpl
import com.jetbrains.python.psi.types.PyUnsafeUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.stream.Stream

/**
 * Covers the decision utilities of [PyTypeUtil] directly, on types that are constructed rather than inferred: the
 * inference tests exercise these through whichever branch a piece of Python code happens to take, which leaves the
 * composite-kind branches (union / unsafe union / intersection) and the empty and non-composite cases untested.
 */
@TestFor(issues = ["PY-91546"], classes = [PyTypeUtil::class])
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyTypeUtilTest : PyCodeInsightTestCase() {

  @Test
  fun `only unions decompose into a union sequence`() {
    withTypes {
      assertEquals(listOf(int, str), union(int, str).asUnionSequence().toList())
      assertEquals(listOf(int, str), unsafeUnion(int, str).asUnionSequence().toList())
      // An intersection is a composite too, but it is not a union, so it stays whole.
      val intersection = intersection(int, str)
      assertEquals(listOf(intersection), intersection.asUnionSequence().toList())
      assertEquals(listOf(int), int.asUnionSequence().toList())
      // `Unknown` is a type of its own, and so is the `null` the extension still accepts as a receiver.
      assertEquals(listOf(PyAnyType.unknown), PyAnyType.unknown.asUnionSequence().toList())
      assertEquals(listOf<PyType?>(null), null.asUnionSequence().toList())
    }
  }

  @Test
  fun `every composite decomposes into its components`() {
    withTypes {
      assertEquals(listOf(int, str), union(int, str).compositeComponents)
      assertEquals(listOf(int, str), unsafeUnion(int, str).compositeComponents)
      assertEquals(listOf(int, str), intersection(int, str).compositeComponents)
      assertEquals(listOf(int), int.compositeComponents)
      assertEquals(listOf(PyAnyType.unknown), PyAnyType.unknown.compositeComponents)
      assertEquals(listOf<PyType?>(null), null.compositeComponents)
      assertEquals(listOf(int, str), intersection(int, str).compositeComponentSequence.toList())
    }
  }

  @Test
  fun `a transformed composite keeps its kind`() {
    withTypes {
      assertTrue(union(int, str, bool).compositeTransform { it.drop(1) } is PyUnionType)
      assertTrue(unsafeUnion(int, str, bool).compositeTransform { it.drop(1) } is PyUnsafeUnionType)
      assertTrue(intersection(int, str, bool).compositeTransform { it.drop(1) } is PyIntersectionType)
      assertEquals(listOf(str, bool), intersection(int, str, bool).compositeTransform { it.drop(1) }.compositeComponents)
      // A single surviving member is the type itself, whatever the composite it came from.
      assertSame(str, union(int, str).compositeTransform { it.drop(1) })
      assertSame(str, intersection(int, str).compositeTransform { it.drop(1) })
    }
  }

  @Test
  fun `a transformed composite collapses to the identity element of its kind`() {
    withTypes {
      // A union of nothing is the bottom type, an intersection of nothing is the top type.
      assertSame(PyNeverType.NEVER, union(int, str).compositeTransform { emptyList() })
      assertSame(PyNeverType.NEVER, unsafeUnion(int, str).compositeTransform { emptyList() })
      assertSame(PyTopType, intersection(int, str).compositeTransform { emptyList() })
      assertSame(PyNeverType.NEVER, int.compositeTransform { emptyList() })
    }
  }

  @Test
  fun `a non composite type is rebuilt as a union`() {
    withTypes {
      assertSame(str, int.compositeTransform { listOf(str) })
      assertTrue(int.compositeTransform { listOf(int, str) } is PyUnionType)
    }
  }

  @Test
  fun `mapping a composite recurses into nested composites`() {
    withTypes {
      val nested = union(intersection(int, str), bool)
      val seen = mutableListOf<PyType?>()
      val mapped = nested.compositeMap {
        seen.add(it)
        if (it == bool) obj else it
      }
      // The mapper sees the leaves only, never a composite, and the nesting survives the round trip.
      assertEquals(listOf(int, str, bool), seen)
      assertEquals(listOf(intersection(int, str), obj), mapped.compositeComponents)
      // A non-composite type is handed to the mapper as it is.
      assertSame(str, int.compositeMap { str })
    }
  }

  @Test
  fun `the subtype quantifier is all for unions and any for the other composites`() {
    withTypes {
      assertTrue(union(int, str).compositeMatchesAsSubtype { it != null })
      assertFalse(union(int, str).compositeMatchesAsSubtype { it == int })
      assertTrue(intersection(int, str).compositeMatchesAsSubtype { it == int })
      assertTrue(unsafeUnion(int, str).compositeMatchesAsSubtype { it == int })
      assertFalse(intersection(int, str).compositeMatchesAsSubtype { it == obj })
      assertTrue(int.compositeMatchesAsSubtype { it == int })

      // The quantifier reproduces the real relation: `int | bool` is assignable to `int`, `int | str` is not.
      assertTrue(union(int, bool).compositeMatchesAsSubtype { PyTypeChecker.match(int, it, context) })
      assertFalse(union(int, str).compositeMatchesAsSubtype { PyTypeChecker.match(int, it, context) })
    }
  }

  @Test
  fun `the supertype quantifier is the mirror of the subtype one`() {
    withTypes {
      assertTrue(union(int, str).compositeMatchesAsSupertype { it == int })
      assertTrue(unsafeUnion(int, str).compositeMatchesAsSupertype { it == int })
      assertFalse(intersection(int, str).compositeMatchesAsSupertype { it == int })
      assertTrue(intersection(int, str).compositeMatchesAsSupertype { it != null })
      assertTrue(int.compositeMatchesAsSupertype { it == int })

      // `bool` is assignable to `int | str` because of one member, and to `int & str` because of neither.
      assertTrue(union(int, str).compositeMatchesAsSupertype { PyTypeChecker.match(it, bool, context) })
      assertFalse(intersection(int, str).compositeMatchesAsSupertype { PyTypeChecker.match(it, bool, context) })
    }
  }

  @Test
  fun `a collector builds the composite kind it is asked for`() {
    withTypes {
      assertEquals(listOf(int, str), types(int, str).collect(PyTypeUtil.toUnion()).compositeComponents)
      val unsafe = types(int, str).collect(PyTypeUtil.toUnsafeUnion())
      assertTrue(unsafe is PyUnsafeUnionType, "$unsafe")
      assertEquals(listOf(int, str), unsafe.compositeComponents)
      val intersection = types(int, str).collect(PyTypeUtil.toIntersection())
      assertTrue(intersection is PyIntersectionType, "$intersection")
      assertEquals(listOf(int, str), intersection.compositeComponents)
    }
  }

  @Test
  fun `a collector follows the kind of the type it is derived from`() {
    withTypes {
      assertTrue(types(int, str).collect(PyTypeUtil.toUnion(unsafeUnion(int, str))) is PyUnsafeUnionType)
      assertTrue(types(int, str).collect(PyTypeUtil.toUnion(int)) is PyUnionType)
      assertTrue(refs(int, str).collect(PyTypeUtil.toUnionFromRef(unsafeUnion(int, str)))!!.get() is PyUnsafeUnionType)
      assertTrue(refs(int, str).collect(PyTypeUtil.toUnionFromRef(int))!!.get() is PyUnionType)
    }
  }

  @Test
  fun `a collector of references keeps every collected member`() {
    withTypes {
      assertEquals(listOf(int, str), refs(int, str).collect(PyTypeUtil.toUnionFromRef())!!.get().compositeComponents)
      assertEquals(listOf(int, str), refs(int, str).collect(PyTypeUtil.toUnsafeUnionFromRef())!!.get().compositeComponents)
      // A missing reference contributes nothing rather than turning the result into `Any`.
      assertSame(int, Stream.of<Ref<PyType?>?>(null, Ref(int)).collect(PyTypeUtil.toUnionFromRef())!!.get())
      assertNull(Stream.of<Ref<PyType?>?>(null, null).collect(PyTypeUtil.toUnionFromRef()))
    }
  }

  @Test
  fun `a reference wraps a type only when there is one`() {
    withTypes {
      assertSame(int, int.notNullToRef()!!.get())
      // `Unknown` is a type, so it is wrapped like any other; only a missing type is not.
      assertSame(PyAnyType.unknown, PyAnyType.unknown.notNullToRef()!!.get())
      assertNull(null.notNullToRef())
      assertSame(int, Ref(int).derefOrUnknown())
      // No reference at all means "not computed", which is `Unknown` rather than a missing type.
      assertSame(PyAnyType.unknown, null.derefOrUnknown())
    }
  }

  @Test
  fun `the same type is one that is assignable both ways`() {
    withTypes {
      assertTrue(int.isSameType(int, context))
      assertTrue(union(int, str).isSameType(union(str, int), context))
      assertFalse(int.isSameType(str, context))
      // `int` is assignable to `object` but not the other way round, so they are not the same type.
      assertFalse(int.isSameType(obj, context))
      assertFalse(int.isSameType(null, context))
      // `Unknown` is assignable in both directions, so it comes out as the same type as any other one.
      assertTrue(int.isSameType(PyAnyType.unknown, context))
    }
  }

  @Test
  fun `subtype related types are checked in both directions and across unions`() {
    withTypes {
      assertTrue(int.isSubtypeRelated(obj, context))
      assertTrue(obj.isSubtypeRelated(int, context))
      assertFalse(int.isSubtypeRelated(str, context))
      // The check is distributed over the members of a union on either side; `bool` is a subclass of `int`.
      assertTrue(union(str, bool).isSubtypeRelated(int, context))
      assertTrue(int.isSubtypeRelated(union(str, bool), context))
      assertTrue(unsafeUnion(str, bool).isSubtypeRelated(int, context))
      assertFalse(union(str, plainDict).isSubtypeRelated(int, context))
    }
  }

  @Test
  fun `only a parameterized dict is a dict`() {
    withTypes {
      assertTrue(file.toKeywordContainerType(int).isDict())
      // A bare `dict` carries no key or value type, so no code that inspects them can use it.
      assertFalse(plainDict.isDict())
      assertFalse(int.isDict())
    }
  }

  @Test
  fun `a container type is built around its element type`() {
    withTypes {
      val positional = file.toPositionalContainerType(int)!!
      assertTrue(positional.isHomogeneous)
      assertEquals(listOf(int), positional.elementTypes)

      // An unpacked tuple already knows its elements, so `*args: *tuple[int, str]` keeps them as they are.
      val unpacked = file.toPositionalContainerType(PyUnpackedTupleTypeImpl(listOf(int, str), false))!!
      assertFalse(unpacked.isHomogeneous)
      assertEquals(listOf(int, str), unpacked.elementTypes)

      val typeVarTuple = PyTypeVarTupleTypeImpl("Ts")
      assertEquals(listOf<PyType?>(typeVarTuple), file.toPositionalContainerType(typeVarTuple)!!.elementTypes)

      // Keyword containers are always `dict[str, <value type>]`.
      val keywords = file.toKeywordContainerType(int) as PyCollectionTypeImpl
      assertEquals(listOf(str, int), keywords.elementTypes)
    }
  }

  @Test
  fun `a tuple of string literals round trips`() {
    withTypes {
      val tuple = PyTypeUtil.createTupleOfLiteralStringsType(file, listOf("a", "b"))!!
      assertEquals(listOf("a", "b"), PyTypeUtil.extractStringLiteralsFromTupleType(tuple))
      // A homogeneous tuple has no fixed elements to read names from, and a plain element type is not a name.
      assertNull(PyTypeUtil.extractStringLiteralsFromTupleType(file.toPositionalContainerType(str)))
      assertNull(PyTypeUtil.extractStringLiteralsFromTupleType(PyTupleType.create(file, listOf(int, str))))
      assertNull(PyTypeUtil.extractStringLiteralsFromTupleType(int))
    }
  }

  @Test
  fun `a literal type is widened to the class of its value`() {
    withTypes {
      val literal = PyLiteralType.stringLiteral(file, "a")!!
      assertEquals(str, PyTypeUtil.widenLiteralAndNumeric(literal))
      // Literals nested in a tuple are widened as well, so that `("a",)` is not a type of its own.
      val tuple = PyTypeUtil.createTupleOfLiteralStringsType(file, listOf("a"))!!
      assertEquals(listOf(str), (PyTypeUtil.widenLiteralAndNumeric(tuple) as PyTupleType).elementTypes)
      assertSame(int, PyTypeUtil.widenLiteralAndNumeric(int))
    }
  }

  @Test
  fun `an overload set is read and mapped like a single callable`() {
    withTypes("""
      def f(x: int) -> str: ...
      def g() -> int: ...
    """) {
      val f = callableType("f")
      val g = callableType("g")
      val overload = PyOverloadType(listOf(f, g), null)

      assertEquals(listOf(f), PyTypeUtil.getCallableItems(f))
      assertEquals(listOf(f, g), PyTypeUtil.getCallableItems(overload))
      // A class type is callable as well: calling it invokes its constructor.
      assertEquals(listOf<PyCallableType>(int), PyTypeUtil.getCallableItems(int))
      // A union of callables is not a signature of its own, so there is nothing to read or map.
      val notCallable = union(int, str)
      assertTrue(PyTypeUtil.getCallableItems(notCallable).isEmpty())

      assertSame(g, PyTypeUtil.mapCallableType(f) { g })
      assertEquals(listOf(g, f), PyTypeUtil.getCallableItems(PyTypeUtil.mapCallableType(overload) { if (it == f) g else f }))
      // An overload set with a single surviving signature is that signature.
      assertSame(g, PyTypeUtil.mapCallableType(overload) { if (it == g) it else null })
      assertSame(notCallable, PyTypeUtil.mapCallableType(notCallable) { error("the mapper must not be called") })
    }
  }

  @Test
  fun `class members are filtered by their psi type`() {
    withTypes("""
      class C:
          attr = 1

          def m(self): pass
    """) {
      val c = classType("C")
      assertEquals(listOf("m"), PyTypeUtil.getMembersOfType(c, PyFunction::class.java, false, context).map { it.name })
      assertEquals(listOf("attr"), PyTypeUtil.getMembersOfType(c, PyTargetExpression::class.java, false, context).map { it.name })
      assertEquals(listOf("m"), c.getMembers<PyFunction>(false, context).map { it.name })
      // Only the inherited lookup reaches the members of `object`.
      assertTrue(c.getMembers<PyFunction>(true, context).map { it.name }.contains("__init__"))
    }
  }

  @Test
  fun `data attached to a type is found through the members of a union`() {
    withTypes("class C: pass") {
      val key = Key.create<String>("PyTypeUtilTest.data")
      val c = classType("C")
      c.putUserData(key, "attached")

      assertEquals("attached", c.findData(key))
      assertEquals("attached", union(str, c)!!.findData(key))
      assertNull(union(str, int)!!.findData(key))
      // Only unions are searched: other composites are not data holders and have no members to descend into.
      assertNull(intersection(str, c)!!.findData(key))
    }
  }

  @Test
  fun `a containing class is taken from the first resolved class member`() {
    withTypes("""
      class C:
          def m(self): pass

      def free(): pass
    """) {
      assertEquals("C", PyTypeUtil.getContainingClass(resolveResults("C.m"))?.name)
      assertNull(PyTypeUtil.getContainingClass(resolveResults("free")))
      assertNull(PyTypeUtil.getContainingClass(emptyList()))
    }
  }

  @Test
  fun `the components of a hint include every type it mentions`() {
    withTypes {
      val components = union(int, str).collectTypeComponents(context)
      assertTrue(components.containsAll(listOf(int, str)), "$components")
      assertEquals(setOf(int), int.collectTypeComponents(context))
    }
  }

  private fun <R> withTypes(@Language("Python") text: String = "", body: Types.() -> R): R {
    val file = myFixture.configureByText("aaa.py", text.trimIndent()) as PyFile
    return runReadActionBlocking { Types(file).body() }
  }

  private class Types(val file: PyFile) {
    val context: TypeEvalContext = TypeEvalContext.userInitiated(myFixture.project, file)

    private val builtins: PyBuiltinCache = PyBuiltinCache.getInstance(file)
    val int: PyClassType = builtins.intType!!
    val str: PyClassType = builtins.strType!!
    val bool: PyClassType = builtins.boolType!!
    val obj: PyClassType = builtins.objectType!!
    val plainDict: PyClassType = builtins.dictType!!

    fun union(vararg types: PyType?): PyType? = PyUnionType.union(types.toList())

    fun unsafeUnion(vararg types: PyType?): PyType? = PyUnsafeUnionType.unsafeUnion(types.toList())

    fun intersection(vararg types: PyType?): PyType? = PyIntersectionType.intersectionOrTop(types.toList())

    fun types(vararg types: PyType?): Stream<PyType?> = Stream.of(*types)

    fun refs(vararg types: PyType?): Stream<Ref<PyType?>?> {
      val refs: Array<Ref<PyType?>?> = Array(types.size) { Ref(types[it]) }
      return Stream.of(*refs)
    }

    fun classType(name: String): PyClassTypeImpl = PyClassTypeImpl(file.findTopLevelClass(name)!!, false)

    fun callableType(name: String): PyCallableType = context.getType(function(name)) as PyCallableType

    fun function(name: String): PyFunction = findFunction(file, name)

    fun resolveResults(functionName: String): List<RatedResolveResult> =
      listOf(RatedResolveResult(RatedResolveResult.RATE_NORMAL, function(functionName)))
  }
}
